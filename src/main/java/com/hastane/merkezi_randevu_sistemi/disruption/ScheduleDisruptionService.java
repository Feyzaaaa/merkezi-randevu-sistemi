package com.hastane.merkezi_randevu_sistemi.disruption;

import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.*;
import com.hastane.merkezi_randevu_sistemi.rules.AppointmentScheduleRules;
import com.hastane.merkezi_randevu_sistemi.service.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PROGRAM BOZULMASI YÖNETİMİ (Schedule Disruption)
 *
 * Çakışma yönetiminin ikinci yarısıdır. Mevcut kural katmanı, randevu ALINIRKEN
 * doğan çakışmaları önler. Bu servis ise plan kurulduktan SONRA dışarıdan gelen
 * bozulmayı çözer: doktor hastalandı, acil görevlendirildi, izin aldı.
 *
 * TEMEL İLKE — MİNİMAL MÜDAHALE
 * Bozulmayı çözerken mevcut plan mümkün olduğunca korunur. Etkilenmeyen randevulara
 * dokunulmaz; etkilenenler de eski saatlerine en yakın slotlara yerleştirilir.
 * Maliyet fonksiyonuna bu amaçla bir KAYMA terimi eklenir:
 *
 *     Maliyet = Bekleme + Doluluk + |yeni zaman − eski zaman| × kaymaAgirligi
 *
 * BOZULMA BÜTÇESİ (kademeli taşıma)
 * Etkilenen hastanın en uygun slotu başka bir randevu tarafından doluysa, o randevu
 * da taşınabilir. Ancak bu, bozulmayı yaymak demektir: kaydırılan hasta da iptal +
 * öneri sürecinden geçer. Bu yüzden "en fazla K randevuya dokun" bütçesiyle sınırlanır
 * ve zincir TEK SEVİYEDE kesilir — taşınan randevu yalnızca BOŞ bir slota gidebilir,
 * üçüncü bir hastayı rahatsız edemez.
 *
 * HASTA ÖZERKLİĞİ
 * Randevular sessizce taşınmaz. Doktor o gün bulunmayacağı için eski randevu iptal
 * edilir, hastaya bir slot REZERVE EDİLİR ve öneri gönderilir. Hasta kabul ederse
 * yeni randevu oluşur; reddeder veya süre dolarsa rezervasyon serbest kalır.
 */
@Service
public class ScheduleDisruptionService {

    /** Kayma maliyetinin ağırlığı: saat başına eklenen maliyet */
    private static final double KAYMA_AGIRLIGI = 0.02;

    /** Önerinin geçerlilik süresi (gün) */
    private static final int ONERI_GECERLILIK_GUN = 3;

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final RescheduleProposalRepository proposalRepository;
    private final EmailService emailService;

    public ScheduleDisruptionService(AppointmentRepository appointmentRepository,
                                     DoctorRepository doctorRepository,
                                     DoctorLeaveRepository doctorLeaveRepository,
                                     RescheduleProposalRepository proposalRepository,
                                     EmailService emailService) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.doctorLeaveRepository = doctorLeaveRepository;
        this.proposalRepository = proposalRepository;
        this.emailService = emailService;
    }

    // ================= ÖNİZLEME =================

    /**
     * Bozulmanın sonucunu HESAPLAR ama UYGULAMAZ. Doktor, izni onaylamadan önce
     * kaç hastanın etkileneceğini ve kaçına alternatif bulunduğunu görür.
     *
     * @param butce kademeli taşımada dokunulabilecek en fazla ek randevu sayısı (0 = katı minimal müdahale)
     */
    public DisruptionPlan onizle(Long doctorId, LocalDate bozulanGun, String gerekce, int butce) {
        LocalDateTime simdi = LocalDateTime.now();

        Doctor doktor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doktor bulunamadı: " + doctorId));

        // Birincil etkilenenler: bozulan gündeki iptal edilmemiş randevular.
        // Sıra adaleti: randevuyu ÖNCE alan ÖNCE yerleşir (FCFS).
        List<Appointment> etkilenenler = appointmentRepository
                .findByDoctorIdAndAppointmentDateBetween(doctorId, bozulanGun.atStartOfDay(), bozulanGun.atTime(LocalTime.MAX))
                .stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .sorted(Comparator.comparing(
                        a -> a.getCreatedAt() == null ? a.getAppointmentDate() : a.getCreatedAt()))
                .toList();

        // Yerleştirme sırasında doluluk durumu adım adım güncellenir
        Yerlesim yerlesim = new Yerlesim(simdi, bozulanGun, etkilenenler);

        List<DisruptionPlan.Kalem> kalemler = new ArrayList<>();
        int kullanilanButce = 0;

        for (Appointment etkilenen : etkilenenler) {
            Aday enIyi = enIyiAdayiBul(etkilenen, yerlesim, simdi, kullanilanButce < butce);

            if (enIyi == null) {
                kalemler.add(kalem(etkilenen, null, null, false, "ALTERNATİF YOK"));
                continue;
            }

            // Kademeli taşıma: bu slotta oturan randevu varsa o da yerinden edilir
            if (enIyi.yerindenEdilen() != null) {
                Aday bosSlot = enIyiAdayiBul(enIyi.yerindenEdilen(), yerlesim, simdi, false);
                if (bosSlot == null) {
                    // Yerinden edileni yerleştiremiyorsak kademeli taşımadan vazgeç
                    enIyi = enIyiAdayiBul(etkilenen, yerlesim, simdi, false);
                    if (enIyi == null) {
                        kalemler.add(kalem(etkilenen, null, null, false, "ALTERNATİF YOK"));
                        continue;
                    }
                } else {
                    kullanilanButce++;
                    // Yerinden edilen randevu yeni yerine gider; eski slotu etkilenen hastaya
                    // devredilir. Bir daha taşınmaması için taşınanlar kümesine yazılır.
                    yerlesim.isgalEt(bosSlot.doktorId(), bosSlot.slot());
                    yerlesim.tasindiIsaretle(enIyi.yerindenEdilen());

                    kalemler.add(kalem(enIyi.yerindenEdilen(), bosSlot.slot(), bosSlot.doktorId(),
                            true, "TAŞINACAK"));
                }
            }

            yerlesim.isgalEt(enIyi.doktorId(), enIyi.slot());
            kalemler.add(kalem(etkilenen, enIyi.slot(), enIyi.doktorId(), false, "TAŞINACAK"));
        }

        return new DisruptionPlan(doctorId, doktorAdi(doktor), bozulanGun, gerekce, butce,
                kalemler, olcumHesapla(kalemler, etkilenenler.size(), simdi));
    }

    // ================= UYGULAMA =================

    /**
     * Planı tek bir işlemde uygular: etkilenen randevular iptal edilir, alternatif
     * bulunanlar için slot rezerve edilip hastaya öneri gönderilir.
     *
     * Ya tamamı uygulanır ya hiçbiri: yarım kalmış bir bozulma çözümü, planı
     * düzeltmek yerine daha da bozardı.
     */
    @Transactional
    public DisruptionPlan uygula(DisruptionPlan plan) {
        LocalDateTime simdi = LocalDateTime.now();

        for (DisruptionPlan.Kalem kalem : plan.kalemler()) {
            Appointment randevu = appointmentRepository.findById(kalem.appointmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + kalem.appointmentId()));

            // Doktor o gün bulunmayacağı için eski randevu ayakta kalamaz
            randevu.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(randevu);

            if (!kalem.cozuldu()) {
                bilgilendir(randevu, null, plan.gerekce());
                continue;
            }

            Doctor yeniDoktor = doctorRepository.findById(kalem.yeniDoktorId())
                    .orElseThrow(() -> new IllegalArgumentException("Doktor bulunamadı"));

            RescheduleProposal oneri = new RescheduleProposal();
            oneri.setOriginalAppointment(randevu);
            oneri.setPatient(randevu.getPatient());
            oneri.setDoctor(yeniDoktor);
            oneri.setProposedDate(kalem.yeniTarih());
            oneri.setOriginalDate(kalem.eskiTarih());
            oneri.setReason(plan.gerekce());
            oneri.setCascaded(kalem.kademeli());
            oneri.setCreatedAt(simdi);
            oneri.setExpiresAt(enErkenSon(simdi.plusDays(ONERI_GECERLILIK_GUN), kalem.yeniTarih()));
            proposalRepository.save(oneri);

            bilgilendir(randevu, oneri, plan.gerekce());
        }
        return plan;
    }

    // ================= HASTA YANITI =================

    /** Hasta öneriyi kabul eder: rezerve slotta yeni randevu oluşturulur */
    @Transactional
    public Appointment oneriyiKabulEt(Long oneriId, Long patientId) {
        RescheduleProposal oneri = oneriBul(oneriId, patientId);

        if (!oneri.rezervasyonAktifMi(LocalDateTime.now())) {
            throw new IllegalArgumentException("Bu önerinin süresi dolmuş veya yanıtlanmış!");
        }

        Appointment yeni = new Appointment();
        yeni.setPatient(oneri.getPatient());
        yeni.setDoctor(oneri.getDoctor());
        yeni.setAppointmentDate(oneri.getProposedDate());
        yeni.setStatus(AppointmentStatus.PENDING);
        yeni.setCreatedAt(LocalDateTime.now());
        if (oneri.getOriginalAppointment() != null) {
            yeni.setComplaint(oneri.getOriginalAppointment().getComplaint());
        }
        Appointment kaydedilen = appointmentRepository.save(yeni);

        oneri.setStatus(ProposalStatus.ACCEPTED);
        proposalRepository.save(oneri);
        return kaydedilen;
    }

    /** Hasta öneriyi reddeder: rezervasyon serbest bırakılır */
    @Transactional
    public void oneriyiReddet(Long oneriId, Long patientId) {
        RescheduleProposal oneri = oneriBul(oneriId, patientId);
        if (oneri.getStatus() != ProposalStatus.PENDING) {
            throw new IllegalArgumentException("Bu öneri zaten yanıtlanmış!");
        }
        oneri.setStatus(ProposalStatus.REJECTED);
        proposalRepository.save(oneri);
    }

    public List<RescheduleProposal> hastaninOnerileri(Long patientId) {
        return proposalRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    private RescheduleProposal oneriBul(Long oneriId, Long patientId) {
        RescheduleProposal oneri = proposalRepository.findById(oneriId)
                .orElseThrow(() -> new IllegalArgumentException("Öneri bulunamadı: " + oneriId));
        if (!oneri.getPatient().getId().equals(patientId)) {
            throw new IllegalArgumentException("Bu öneri size ait değil!");
        }
        return oneri;
    }

    // ================= ADAY ARAMA =================

    private record Aday(Long doktorId, LocalDateTime slot, double maliyet, Appointment yerindenEdilen) {}

    /**
     * Bir randevu için en düşük maliyetli alternatifi bulur.
     * Maliyet: bekleme + doluluk sapması + eski saatten kayma.
     *
     * @param kademeliIzinli bütçe varsa dolu slotlar da (sahibini taşımak kaydıyla) değerlendirilir
     */
    private Aday enIyiAdayiBul(Appointment randevu, Yerlesim yerlesim, LocalDateTime simdi, boolean kademeliIzinli) {
        LocalDateTime eski = randevu.getAppointmentDate();
        Aday enIyi = null;

        for (Doctor doktor : yerlesim.uygunDoktorlar()) {
            for (int gun = 0; gun <= AppointmentScheduleRules.MAX_ADVANCE_DAYS; gun++) {
                LocalDate tarih = simdi.toLocalDate().plusDays(gun);

                if (tarih.equals(yerlesim.bozulanGun()) && doktor.getId().equals(yerlesim.bozulanDoktorId())) continue;
                if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doktor.getId(), tarih)) continue;

                LocalTime saat = AppointmentScheduleRules.WORK_START;
                while (!saat.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES)
                        .isAfter(AppointmentScheduleRules.WORK_END)) {
                    LocalDateTime aday = tarih.atTime(saat);
                    saat = saat.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES);

                    if (!AppointmentScheduleRules.isSelectable(aday, simdi)) continue;
                    if (yerlesim.hastaMesgulMu(randevu.getPatient().getId(), aday)) continue;

                    // DİKKAT: "kayıt yok" ile "sahibi bilinmeyen dolu slot" farklıdır.
                    // Bu ayrım yapılmazsa, plan içinde az önce atanmış bir slot boş
                    // sanılıp ikinci bir hastaya da verilir ve rezervasyon çakışır.
                    Appointment sahip = null;
                    if (yerlesim.slotDoluMu(doktor.getId(), aday)) {
                        sahip = yerlesim.slotSahibi(doktor.getId(), aday);
                        // Sahibi bilinmiyorsa (rezerve ya da bu planda atanmış) kullanılamaz
                        if (sahip == null) continue;
                        // Dolu slot: yalnızca bütçe varsa ve sahibi taşınabiliyorsa değerlendirilir
                        if (!kademeliIzinli || !yerlesim.tasinabilirMi(sahip)) continue;
                    }

                    double maliyet = maliyetHesapla(aday, eski, doktor.getId(), tarih, yerlesim, simdi)
                            + (sahip != null ? KADEMELI_CEZASI : 0);

                    if (enIyi == null || maliyet < enIyi.maliyet()) {
                        enIyi = new Aday(doktor.getId(), aday, maliyet, sahip);
                    }
                }
            }
        }
        return enIyi;
    }

    /** Kademeli taşımanın sabit ek maliyeti: bir hastayı daha rahatsız etmenin bedeli */
    private static final double KADEMELI_CEZASI = 0.30;

    private double maliyetHesapla(LocalDateTime aday, LocalDateTime eski, Long doktorId,
                                  LocalDate tarih, Yerlesim yerlesim, LocalDateTime simdi) {
        long beklemeGun = Math.max(Duration.between(simdi, aday).toDays(), 0);
        double bekleme = (double) beklemeGun / AppointmentScheduleRules.MAX_ADVANCE_DAYS;

        double doluluk = yerlesim.dolulukOrani(doktorId, tarih);
        double dolulukMaliyeti = Math.abs(doluluk - 0.85);

        // MİNİMAL MÜDAHALE: eski saatten uzaklaştıkça artan maliyet
        double kaymaSaat = Math.abs(Duration.between(eski, aday).toHours());
        double kayma = kaymaSaat * KAYMA_AGIRLIGI;

        return bekleme + dolulukMaliyeti + kayma;
    }

    // ================= ÖLÇÜM =================

    private DisruptionPlan.Olcum olcumHesapla(List<DisruptionPlan.Kalem> kalemler, int birincil, LocalDateTime simdi) {
        int kademeli = (int) kalemler.stream().filter(DisruptionPlan.Kalem::kademeli).count();
        var cozulenler = kalemler.stream().filter(DisruptionPlan.Kalem::cozuldu).toList();
        int cozulemeyen = kalemler.size() - cozulenler.size();

        double ortalama = cozulenler.stream().mapToLong(DisruptionPlan.Kalem::kaymaDakika).average().orElse(0);
        long maksimum = cozulenler.stream().mapToLong(DisruptionPlan.Kalem::kaymaDakika).max().orElse(0);

        long toplamAktif = appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isAfter(simdi))
                .count();

        int dokunulan = birincil + kademeli;
        double kararlilik = toplamAktif == 0 ? 1.0 : Math.max(0, (double) (toplamAktif - dokunulan) / toplamAktif);

        return new DisruptionPlan.Olcum(birincil, kademeli, cozulenler.size(), cozulemeyen,
                ortalama, maksimum, kararlilik, (int) toplamAktif);
    }

    // ================= YARDIMCILAR =================

    private DisruptionPlan.Kalem kalem(Appointment randevu, LocalDateTime yeniTarih, Long yeniDoktorId,
                                       boolean kademeli, String durum) {
        String yeniDoktorAdi = yeniDoktorId == null ? null :
                doctorRepository.findById(yeniDoktorId).map(ScheduleDisruptionService::doktorAdi).orElse(null);

        long kayma = yeniTarih == null ? 0
                : Math.abs(Duration.between(randevu.getAppointmentDate(), yeniTarih).toMinutes());

        return new DisruptionPlan.Kalem(
                randevu.getId(),
                randevu.getPatient() == null ? null : randevu.getPatient().getId(),
                hastaAdi(randevu),
                randevu.getAppointmentDate(),
                yeniTarih, yeniDoktorId, yeniDoktorAdi,
                kayma, kademeli, durum);
    }

    private void bilgilendir(Appointment randevu, RescheduleProposal oneri, String gerekce) {
        if (randevu.getPatient() == null || randevu.getPatient().getEmail() == null) return;

        String govde = oneri == null
                ? """
                  Sayın %s,

                  %s tarihli randevunuz, doktorun programındaki değişiklik nedeniyle iptal edilmiştir.
                  Gerekçe: %s

                  Maalesef uygun bir alternatif saat bulunamadı. Portalden yeni randevu alabilirsiniz.

                  MHRS - Merkezi Sağlık Sistemi"""
                  .formatted(randevu.getPatient().getFirstName(), randevu.getAppointmentDate(), gerekce)
                : """
                  Sayın %s,

                  %s tarihli randevunuz, doktorun programındaki değişiklik nedeniyle iptal edilmiştir.
                  Gerekçe: %s

                  Sizin için %s tarihi ayrılmıştır. Portalden onaylayabilir ya da reddedip
                  kendiniz farklı bir saat seçebilirsiniz. Ayrılan saat %s tarihine kadar sizin için tutulacaktır.

                  MHRS - Merkezi Sağlık Sistemi"""
                  .formatted(randevu.getPatient().getFirstName(), randevu.getAppointmentDate(), gerekce,
                          oneri.getProposedDate(), oneri.getExpiresAt());

        emailService.send(randevu.getPatient().getEmail(),
                "Randevunuz Yeniden Planlandı - MHRS", govde);
    }

    private static LocalDateTime enErkenSon(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static String doktorAdi(Doctor d) {
        if (d == null || d.getUser() == null) return "Doktor";
        String unvan = d.getTitle() == null ? "" : d.getTitle() + " ";
        return unvan + d.getUser().getFirstName() + " " + d.getUser().getLastName();
    }

    private static String hastaAdi(Appointment a) {
        if (a.getPatient() == null) return "-";
        return a.getPatient().getFirstName() + " " + a.getPatient().getLastName();
    }

    /**
     * Yerleştirme sırasında güncellenen doluluk durumu.
     * Plan hazırlanırken veritabanına yazmadan "şu slot artık dolu" bilgisini taşır;
     * aksi hâlde iki etkilenen hasta aynı slota yerleştirilirdi.
     */
    private final class Yerlesim {
        private final LocalDate bozulanGun;
        private final Long bozulanDoktorId;
        private final List<Doctor> doktorlar;
        private final Map<String, Appointment> slotSahipleri = new HashMap<>();
        private final Set<String> hastaMesgul = new HashSet<>();
        private final Set<Long> etkilenenIdleri;
        private final Set<Long> tasinanlar = new HashSet<>();

        Yerlesim(LocalDateTime simdi, LocalDate bozulanGun, List<Appointment> etkilenenler) {
            this.bozulanGun = bozulanGun;
            this.bozulanDoktorId = etkilenenler.isEmpty() ? null
                    : etkilenenler.get(0).getDoctor().getId();

            this.etkilenenIdleri = etkilenenler.stream().map(Appointment::getId).collect(Collectors.toSet());
            // ALTERNATİFLER AYNI POLİKLİNİKTEN SEÇİLİR.
            // Bu kısıt olmadan motor, Dahiliye randevusunu Göz Hastalıkları doktoruna
            // atayabiliyordu: aynı gün/saatte boş doktor bulduğu için kayma sıfır
            // görünüyor ama hasta yanlış uzmanlığa yönlendirilmiş oluyordu.
            Long poliklinikId = etkilenenler.isEmpty() ? null
                    : etkilenenler.get(0).getDoctor().getDepartment() == null ? null
                    : etkilenenler.get(0).getDoctor().getDepartment().getId();

            this.doktorlar = doctorRepository.findAll().stream()
                    .filter(d -> d.getDepartment() != null)
                    .filter(d -> poliklinikId == null || poliklinikId.equals(d.getDepartment().getId()))
                    .toList();

            LocalDateTime son = simdi.plusDays(AppointmentScheduleRules.MAX_ADVANCE_DAYS).with(LocalTime.MAX);
            for (Doctor d : doktorlar) {
                appointmentRepository.findByDoctorIdAndAppointmentDateBetween(d.getId(), simdi, son).stream()
                        .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                        .filter(a -> !etkilenenIdleri.contains(a.getId()))
                        .forEach(a -> {
                            slotSahipleri.put(anahtar(d.getId(), a.getAppointmentDate()), a);
                            if (a.getPatient() != null) {
                                hastaMesgul.add(a.getPatient().getId() + "@" + a.getAppointmentDate());
                            }
                        });
                // R11: rezerve slotlar da dolu sayılır
                proposalRepository.findAktifRezervasyonlar(d.getId(), simdi, son, simdi)
                        .forEach(p -> slotSahipleri.put(anahtar(d.getId(), p.getProposedDate()), null));
            }
        }

        List<Doctor> uygunDoktorlar() { return doktorlar; }
        LocalDate bozulanGun() { return bozulanGun; }
        Long bozulanDoktorId() { return bozulanDoktorId; }

        Appointment slotSahibi(Long doktorId, LocalDateTime slot) {
            String k = anahtar(doktorId, slot);
            return slotSahipleri.containsKey(k) ? slotSahipleri.get(k) : null;
        }

        boolean slotDoluMu(Long doktorId, LocalDateTime slot) {
            return slotSahipleri.containsKey(anahtar(doktorId, slot));
        }

        boolean tasinabilirMi(Appointment a) {
            return a != null
                    && !etkilenenIdleri.contains(a.getId())
                    && !tasinanlar.contains(a.getId());
        }

        boolean hastaMesgulMu(Long hastaId, LocalDateTime slot) {
            return hastaMesgul.contains(hastaId + "@" + slot);
        }

        /** Slotu kesin olarak dolu işaretler; sahibi artık bu plan tarafından belirlenmiştir */
        void isgalEt(Long doktorId, LocalDateTime slot) {
            slotSahipleri.put(anahtar(doktorId, slot), null);
        }

        /** Kademeli taşımada yerinden edilen randevu: bir daha taşınamaz */
        void tasindiIsaretle(Appointment a) {
            if (a != null && a.getId() != null) tasinanlar.add(a.getId());
        }

        double dolulukOrani(Long doktorId, LocalDate tarih) {
            long dolu = slotSahipleri.keySet().stream()
                    .filter(k -> k.startsWith(doktorId + "#" + tarih))
                    .count();
            return (double) dolu / 36.0;   // mesai penceresindeki slot sayısı
        }

        private String anahtar(Long doktorId, LocalDateTime slot) {
            return doktorId + "#" + slot;
        }
    }
}
