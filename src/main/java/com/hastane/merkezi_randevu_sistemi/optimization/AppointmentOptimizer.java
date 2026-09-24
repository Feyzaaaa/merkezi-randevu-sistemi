package com.hastane.merkezi_randevu_sistemi.optimization;

import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.*;
import com.hastane.merkezi_randevu_sistemi.rules.AppointmentScheduleRules;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * RANDEVU OPTİMİZASYON MOTORU
 *
 * Soru: "Hasta için mevcut alternatifler arasından en uygun randevu nasıl seçilir?"
 *
 * Kural katmanı (R1–R10) bir adayın GEÇERLİ olup olmadığını söyler; bu sınıf
 * geçerli adaylar arasından hangisinin DAHA İYİ olduğunu belirler. Dört ölçüt
 * ağırlıklı bir maliyet fonksiyonunda birleştirilir; düşük maliyet daha iyidir:
 *
 *   Maliyet = w₁·Bekleme + w₂·Doluluk + w₃·Varyans + w₄·Risk
 *
 * 1) BEKLEME — hasta kaç gün bekleyecek. Erken olan tercih edilir.
 *
 * 2) DOLULUK — doktorun o günkü doluluk oranının HEDEFTEN sapması. Hedef %100
 *    değildir: dolu bir gün mola, gecikme ve acil hasta için pay bırakmaz ve tek
 *    bir gecikme tüm günü kaydırır. Ceza asimetriktir — atıl kapasite israftır,
 *    aşırı yükleme risktir ve daha ağır cezalandırılır.
 *
 * 3) VARYANS — adayın bekleme süresinin, sistemdeki ortalama bekleme süresinden
 *    sapması. Bu terim bekleme dağılımını daraltır: bir hasta 1 gün beklerken
 *    diğerinin 29 gün beklemesi engellenir.
 *
 * 4) RİSK — P(iptal) × o günün doluluğu. İptal olasılığı randevuya kalan süreye
 *    bağlı olduğundan, riskli hasta için motor kendiliğinden daha yakın tarihe
 *    yönelir (riski azaltan yön). Doluluk çarpanı ise en çok talep gören slotların
 *    yüksek riskli randevularla işgal edilmesini sınırlar.
 *    HASTAYA RANDEVU VERİLMEMESİ SÖZ KONUSU DEĞİLDİR: risk yalnızca sıralamayı
 *    etkiler, hiçbir adayı elemez.
 */
@Service
public class AppointmentOptimizer {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final OptimizationWeights agirliklar;

    public AppointmentOptimizer(AppointmentRepository appointmentRepository,
                                DoctorRepository doctorRepository,
                                DoctorLeaveRepository doctorLeaveRepository,
                                OptimizationWeights agirliklar) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.doctorLeaveRepository = doctorLeaveRepository;
        this.agirliklar = agirliklar;
    }

    /** Motorun bir çalıştırmadaki tüm çıktısı */
    public record Sonuc(List<SlotScore> oneriler, int degerlendirilenAday, String modelDurumu) {}

    /**
     * Bir poliklinikteki tüm geçerli adayları değerlendirip en iyilerini döndürür.
     *
     * @param hastaId       öneri istenen hasta
     * @param departmentId  poliklinik (null ise tüm poliklinikler)
     */
    public Sonuc onerileriHesapla(Long hastaId, Long departmentId) {
        LocalDateTime simdi = LocalDateTime.now();

        List<Doctor> doktorlar = doctorRepository.findAll().stream()
                .filter(d -> d.getDepartment() != null)
                .filter(d -> departmentId == null || departmentId.equals(d.getDepartment().getId()))
                .toList();

        if (doktorlar.isEmpty()) {
            return new Sonuc(List.of(), 0, "doktor yok");
        }

        // --- Tek seferde veri toplama (aday başına sorgu atmamak için) ---
        LocalDateTime pencereBas = simdi;
        LocalDateTime pencereSon = simdi.plusDays(AppointmentScheduleRules.MAX_ADVANCE_DAYS).with(LocalTime.MAX);

        Map<Long, List<Appointment>> doktorRandevulari = new HashMap<>();
        for (Doctor d : doktorlar) {
            doktorRandevulari.put(d.getId(),
                    appointmentRepository.findByDoctorIdAndAppointmentDateBetween(d.getId(), pencereBas, pencereSon)
                            .stream()
                            .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                            .toList());
        }

        // (doktor, gün) -> dolu slot sayısı
        Map<String, Integer> gunlukDoluluk = new HashMap<>();
        for (var giris : doktorRandevulari.entrySet()) {
            for (Appointment a : giris.getValue()) {
                gunlukDoluluk.merge(anahtar(giris.getKey(), a.getAppointmentDate().toLocalDate()), 1, Integer::sum);
            }
        }

        // Hastanın kendi randevuları: çakışma ve kural kontrolleri için
        List<Appointment> hastaninRandevulari = appointmentRepository.findByPatientId(hastaId);
        Set<LocalDateTime> hastaninDoluSaatleri = new HashSet<>();
        Set<String> hastaninGunPoliklinikleri = new HashSet<>();
        int aktifRandevu = 0;
        for (Appointment a : hastaninRandevulari) {
            if (a.getStatus() == AppointmentStatus.CANCELLED) continue;
            hastaninDoluSaatleri.add(a.getAppointmentDate());
            if (a.getAppointmentDate().isAfter(simdi)) aktifRandevu++;
            if (a.getDoctor() != null && a.getDoctor().getDepartment() != null) {
                hastaninGunPoliklinikleri.add(
                        a.getAppointmentDate().toLocalDate() + "#" + a.getDoctor().getDepartment().getId());
            }
        }

        // R9: sınıra ulaşıldıysa öneri üretmenin anlamı yok
        if (aktifRandevu >= AppointmentScheduleRules.MAX_ACTIVE_APPOINTMENTS) {
            return new Sonuc(List.of(), 0, "aktif randevu sınırına ulaşıldı");
        }

        // --- İptal riski modeli: sistemdeki geçmiş üzerinde eğitilir ---
        CancellationRiskModel model = new CancellationRiskModel();
        model.egit(appointmentRepository.findAll());
        int gecmisIptal = 0, gecmisToplam = 0;
        for (Appointment a : hastaninRandevulari) {
            if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.COMPLETED) {
                gecmisToplam++;
                if (a.getStatus() == AppointmentStatus.CANCELLED) gecmisIptal++;
            }
        }

        double ortalamaBekleme = ortalamaBeklemeSuresi();

        // --- Aday üretimi ve puanlama ---
        List<SlotScore> adaylar = new ArrayList<>();
        int degerlendirilen = 0;

        for (Doctor doktor : doktorlar) {
            int gunlukSlotSayisi = gunlukSlotSayisi();

            for (int gun = 0; gun <= AppointmentScheduleRules.MAX_ADVANCE_DAYS; gun++) {
                LocalDate tarih = simdi.toLocalDate().plusDays(gun);

                // R7: doktor o gün izinliyse hiç aday üretme
                if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doktor.getId(), tarih)) continue;

                // R8: hasta o gün aynı poliklinikten randevu almışsa bu gün elenir
                if (hastaninGunPoliklinikleri.contains(tarih + "#" + doktor.getDepartment().getId())) continue;

                int oGunDolu = gunlukDoluluk.getOrDefault(anahtar(doktor.getId(), tarih), 0);
                Set<LocalTime> doluSaatler = new HashSet<>();
                for (Appointment a : doktorRandevulari.get(doktor.getId())) {
                    if (a.getAppointmentDate().toLocalDate().equals(tarih)) {
                        doluSaatler.add(a.getAppointmentDate().toLocalTime());
                    }
                }

                LocalTime saat = AppointmentScheduleRules.WORK_START;
                while (!saat.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES)
                        .isAfter(AppointmentScheduleRules.WORK_END)) {
                    LocalDateTime aday = tarih.atTime(saat);
                    saat = saat.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES);

                    // R1–R6: zaman kuralları
                    if (!AppointmentScheduleRules.isSelectable(aday, simdi)) continue;
                    // Doktorun o saati dolu mu?
                    if (doluSaatler.contains(aday.toLocalTime())) continue;
                    // Hastanın o saatte başka randevusu var mı?
                    if (hastaninDoluSaatleri.contains(aday)) continue;

                    degerlendirilen++;
                    adaylar.add(puanla(doktor, aday, simdi, oGunDolu, gunlukSlotSayisi,
                            ortalamaBekleme, model, gecmisIptal, gecmisToplam));
                }
            }
        }

        adaylar.sort(Comparator.comparingDouble(SlotScore::toplamMaliyet));
        return new Sonuc(cesitlendir(adaylar), degerlendirilen, model.ozet());
    }

    /**
     * ÇEŞİTLİLİK KISITI
     *
     * En düşük maliyetli adaylar neredeyse her zaman aynı günün ardışık saatleridir
     * (14:00, 14:15, 14:30...). Bu liste teknik olarak "en iyi üç" olsa da kullanıcıya
     * gerçek bir seçim sunmaz. Bu yüzden her (doktor, gün) çiftinden en fazla bir aday
     * alınır; hasta farklı günler ve farklı doktorlar arasından seçim yapabilir.
     *
     * Yeterli çeşitlilik bulunamazsa (ör. tek doktor, tek uygun gün) liste kısa kalır;
     * yapay olarak doldurulmaz.
     */
    private List<SlotScore> cesitlendir(List<SlotScore> siraliAdaylar) {
        List<SlotScore> secilenler = new ArrayList<>();
        Set<String> kullanilanGunler = new HashSet<>();

        for (SlotScore aday : siraliAdaylar) {
            if (secilenler.size() >= agirliklar.getOneriSayisi()) break;

            String gunAnahtari = anahtar(aday.doctorId(), aday.tarih().toLocalDate());
            if (kullanilanGunler.add(gunAnahtari)) {
                secilenler.add(aday);
            }
        }
        return secilenler;
    }

    // --- MALİYET HESABI ---

    private SlotScore puanla(Doctor doktor, LocalDateTime aday, LocalDateTime simdi,
                             int oGunDoluSlot, int gunlukSlotSayisi, double ortalamaBekleme,
                             CancellationRiskModel model, int gecmisIptal, int gecmisToplam) {

        long beklemeGun = Math.max(Duration.between(simdi, aday).toDays(), 0);

        // 1) BEKLEME: erken olan iyi
        double beklemeMaliyeti = (double) beklemeGun / AppointmentScheduleRules.MAX_ADVANCE_DAYS;

        // 2) DOLULUK: bu randevu EKLENDİKTEN SONRAKİ doluluk hedeften ne kadar sapıyor?
        double dolulukSonra = (double) (oGunDoluSlot + 1) / gunlukSlotSayisi;
        double dolulukMaliyeti = dolulukMaliyetiHesapla(dolulukSonra);

        // 3) VARYANS: sistemin ortalama beklemesinden sapma
        double varyansMaliyeti = ortalamaBekleme < 0
                ? 0.0   // geçmiş veri yoksa bu terim devre dışı
                : Math.min(Math.abs(beklemeGun - ortalamaBekleme) / AppointmentScheduleRules.MAX_ADVANCE_DAYS, 1.0);

        // 4) RİSK: iptal olasılığı × slotun ne kadar talep gördüğü
        double iptalOlasiligi = model.tahminEt(beklemeGun, gecmisIptal, gecmisToplam, aday.getHour());
        double dolulukOnce = (double) oGunDoluSlot / gunlukSlotSayisi;
        double riskMaliyeti = iptalOlasiligi * dolulukOnce;

        double toplam = agirliklar.getBeklemeAgirligi() * beklemeMaliyeti
                + agirliklar.getDolulukAgirligi() * dolulukMaliyeti
                + agirliklar.getVaryansAgirligi() * varyansMaliyeti
                + agirliklar.getRiskAgirligi() * riskMaliyeti;

        return new SlotScore(
                doktor.getId(),
                doktorAdi(doktor),
                doktor.getDepartment().getName(),
                aday,
                beklemeGun,
                beklemeMaliyeti, dolulukMaliyeti, varyansMaliyeti, riskMaliyeti,
                dolulukSonra, iptalOlasiligi,
                toplam);
    }

    /**
     * ASİMETRİK DOLULUK MALİYETİ
     *
     * Hedefin altı  : atıl kapasite — israf, hafif ceza
     * Hedefin üstü  : aşırı yükleme — mola/gecikme/acil payı kalmaz, ağır ceza
     *
     * %85 hedefte 0, boş günde ~1, tamamen dolu günde ~2 maliyet üretir.
     */
    double dolulukMaliyetiHesapla(double doluluk) {
        double hedef = agirliklar.getHedefDoluluk();
        if (doluluk <= hedef) {
            return (hedef - doluluk) / hedef;
        }
        return agirliklar.getAsiriYukCezasi() * (doluluk - hedef) / Math.max(1 - hedef, 0.01);
    }

    /** Sistemdeki aktif randevuların ortalama bekleme süresi; veri yoksa -1 */
    private double ortalamaBeklemeSuresi() {
        var beklemeler = appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .map(Appointment::getWaitDays)
                .filter(OptionalLong::isPresent)
                .mapToLong(OptionalLong::getAsLong)
                .filter(g -> g >= 0)
                .toArray();

        if (beklemeler.length == 0) return -1;
        return Arrays.stream(beklemeler).average().orElse(-1);
    }

    /** Mesai penceresindeki toplam slot sayısı (öğle arası hariç) */
    private int gunlukSlotSayisi() {
        int sayi = 0;
        LocalTime t = AppointmentScheduleRules.WORK_START;
        while (!t.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES).isAfter(AppointmentScheduleRules.WORK_END)) {
            boolean ogleArasi = t.isBefore(AppointmentScheduleRules.LUNCH_END)
                    && t.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES).isAfter(AppointmentScheduleRules.LUNCH_START);
            if (!ogleArasi) sayi++;
            t = t.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES);
        }
        return sayi;
    }

    private static String anahtar(Long doktorId, LocalDate tarih) {
        return doktorId + "#" + tarih;
    }

    private static String doktorAdi(Doctor d) {
        if (d.getUser() == null) return "Doktor";
        String unvan = d.getTitle() == null ? "" : d.getTitle() + " ";
        return unvan + d.getUser().getFirstName() + " " + d.getUser().getLastName();
    }
}
