package com.hastane.merkezi_randevu_sistemi.disruption;

import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.*;
import com.hastane.merkezi_randevu_sistemi.rules.AppointmentScheduleRules;
import com.hastane.merkezi_randevu_sistemi.service.EmailService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOZULMA ÇÖZÜM POLİTİKALARININ KARŞILAŞTIRILMASI
 *
 * Tez sorusu: "Bir doktorun programı bozulduğunda mevcut plan ne kadar korunmalı?"
 *
 * Aynı bozulma senaryosu farklı bozulma bütçeleriyle çözülür ve ölçülür:
 *
 *   BÜTÇE 0   Katı minimal müdahale — etkilenmeyen hiçbir randevuya dokunulmaz
 *   BÜTÇE K   Kademeli taşıma — en fazla K "masum" randevu da yerinden edilebilir
 *
 * Beklenen ödünleşim: bütçe arttıkça etkilenen hastalar eski saatlerine daha yakın
 * slotlara yerleşir (ortalama kayma düşer), fakat toplam rahatsız edilen hasta
 * sayısı artar ve plan kararlılığı düşer.
 *
 * Referans nokta olarak "toplu iptal" (sistemin ilk hâlindeki davranış) da ölçülür:
 * hiçbir randevu kurtarılmaz.
 */
@SpringBootTest
class DisruptionPolicyExperimentTest {

    private static final String TEST_EPOSTA_SONU = "@bozulma.local";

    /** Bozulan doktorun o gün kaç randevusu var */
    private static final int ETKILENEN_RANDEVU = 12;

    /**
     * Alternatif kapasitenin ne kadarı dolu.
     * Kıtlık olmadan ödünleşim görünmez: herkes rahatça boş slota yerleşirse
     * kademeli taşımaya hiç gerek kalmaz ve bütçe eğrisi düz çıkar.
     */
    private static final double ALTERNATIF_DOLULUK = 1.00;

    @Autowired private ScheduleDisruptionService disruptionService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private RescheduleProposalRepository proposalRepository;

    @MockitoBean private EmailService emailService;

    private Department poliklinik;
    private Doctor bozulanDoktor;
    private LocalDate bozulanGun;

    @BeforeEach
    void hazirla() {
        temizle();
        bozulanGun = LocalDate.now().plusDays(2);

        poliklinik = departmentRepository.save(new Department("Bozulma Polikliniği " + System.nanoTime()));
        bozulanDoktor = doktorOlustur("Bozulan");
        Doctor alternatif1 = doktorOlustur("Alternatif1");
        Doctor alternatif2 = doktorOlustur("Alternatif2");

        // Bozulan doktorun o günkü randevuları: her biri FARKLI hasta
        List<LocalTime> saatler = uygunSaatler();
        for (int i = 0; i < ETKILENEN_RANDEVU; i++) {
            User hasta = hastaOlustur("etkilenen" + i);
            randevuOlustur(hasta, bozulanDoktor, bozulanGun.atTime(saatler.get(i)));
        }

        // Bozulan doktorun KOMŞU günleri de dolu: eski saate en yakın slotlar kapalı olsun.
        // Böylece "az kaymalı ama dolu" slotlar ile "boş ama uzak" slotlar arasında
        // gerçek bir tercih doğar ve bütçenin etkisi ölçülebilir hâle gelir.
        for (int gunFarki : new int[]{1, 3}) {
            LocalDate komsu = bozulanGun.plusDays(gunFarki - 2);
            if (!komsu.isAfter(LocalDate.now())) continue;
            User dolguHasta = hastaOlustur("komsu-" + gunFarki);
            for (int i = 0; i < saatler.size(); i++) {
                randevuOlustur(dolguHasta, bozulanDoktor, komsu.atTime(saatler.get(i)));
            }
        }

        // Alternatif doktorların günleri de dolu
        for (Doctor alt : List.of(alternatif1, alternatif2)) {
            for (int gunFarki = 0; gunFarki <= 4; gunFarki++) {
                LocalDate gun = LocalDate.now().plusDays(gunFarki);
                User dolguHasta = hastaOlustur("dolgu-" + alt.getId() + "-" + gunFarki);
                int adet = (int) Math.round(saatler.size() * ALTERNATIF_DOLULUK);
                for (int i = 0; i < adet; i++) {
                    randevuOlustur(dolguHasta, alt, gun.atTime(saatler.get(i)));
                }
            }
        }
    }

    @AfterEach
    void toparla() {
        temizle();
    }

    // ================= DENEY =================

    @Test
    @DisplayName("DENEY: Bozulma bütçesi arttıkça kayma azalır ama daha fazla hasta rahatsız edilir")
    void butceEgrisi() {
        StringBuilder rapor = new StringBuilder("""

                  BOZULMA ÇÖZÜM POLİTİKALARI — %d randevuluk bir gün iptal oldu
                """.formatted(ETKILENEN_RANDEVU));
        rapor.append("  ").append("─".repeat(92)).append("\n");
        rapor.append("  %-26s %10s %10s %12s %12s %12s%n".formatted(
                "POLİTİKA", "ÇÖZÜLEN", "ÇÖZÜLMEYEN", "EK RAHATSIZ", "ORT. KAYMA", "KARARLILIK"));
        rapor.append("  ").append("─".repeat(92)).append("\n");

        // Referans: toplu iptal (sistemin ilk hâli) — hiçbir randevu kurtarılmaz
        rapor.append("  %-26s %10d %10d %12s %12s %12s%n".formatted(
                "Toplu iptal (referans)", 0, ETKILENEN_RANDEVU, "-", "-", "-"));

        List<DisruptionPlan> planlar = new ArrayList<>();
        for (int butce : new int[]{0, 3, 6, 12}) {
            DisruptionPlan plan = disruptionService.onizle(
                    bozulanDoktor.getId(), bozulanGun, "Doktor acil görevlendirildi", butce);
            planlar.add(plan);

            var o = plan.olcum();
            rapor.append("  %-26s %10d %10d %12d %10.1f sa %11.1f%%%n".formatted(
                    butce == 0 ? "Minimal müdahale (K=0)" : "Kademeli (K=" + butce + ")",
                    o.cozulen(), o.cozulemeyen(), o.kademeliEtkilenen(),
                    o.ortalamaKaymaDakika() / 60.0, o.kararlilikOrani() * 100));
        }

        rapor.append("  ").append("─".repeat(92)).append("\n");
        rapor.append("  ÇÖZÜLEN: alternatif slot bulunan randevu · EK RAHATSIZ: bütçe ile yerinden edilen 'masum' hasta\n");
        rapor.append("  KARARLILIK: sistemdeki aktif randevuların yüzde kaçına hiç dokunulmadığı\n");
        System.out.println(rapor);

        DisruptionPlan butcesiz = planlar.get(0);
        DisruptionPlan butceli = planlar.get(planlar.size() - 1);

        // Katı minimal müdahalede hiçbir "masum" randevu yerinden edilmez
        assertEquals(0, butcesiz.olcum().kademeliEtkilenen(),
                "Bütçe 0 iken etkilenmeyen hiçbir randevuya dokunulmamalı");

        // Bütçe arttıkça kararlılık düşer (daha fazla randevuya dokunulur)
        assertTrue(butceli.olcum().kararlilikOrani() <= butcesiz.olcum().kararlilikOrani(),
                "Bütçe kullanıldıkça plan kararlılığı artamaz");

        // Her iki politikada da etkilenen sayısı doğru sayılmalı
        assertEquals(ETKILENEN_RANDEVU, butcesiz.olcum().birincilEtkilenen());
    }

    @Test
    @DisplayName("Minimal müdahale: etkilenmeyen randevular olduğu yerde kalır")
    void etkilenmeyenlereDokunulmaz() {
        DisruptionPlan plan = disruptionService.onizle(
                bozulanDoktor.getId(), bozulanGun, "Doktor raporlu", 0);

        // Plandaki her kalem, yalnızca bozulan gündeki randevulara ait olmalı
        for (var kalem : plan.kalemler()) {
            assertEquals(bozulanGun, kalem.eskiTarih().toLocalDate(),
                    "Bozulan gün dışındaki bir randevu plana girmiş: " + kalem.hastaAdi());
        }
        assertEquals(ETKILENEN_RANDEVU, plan.kalemler().size());
    }

    @Test
    @DisplayName("Öneriler eski saate yakın slotlara yerleşir (minimal kayma)")
    void kaymaMakulSinirlarda() {
        DisruptionPlan plan = disruptionService.onizle(
                bozulanDoktor.getId(), bozulanGun, "Doktor izinli", 0);

        var cozulenler = plan.kalemler().stream().filter(DisruptionPlan.Kalem::cozuldu).toList();
        assertFalse(cozulenler.isEmpty(), "En az bir randevuya alternatif bulunmalıydı");

        long maksimumKaymaGun = plan.olcum().maksimumKaymaDakika() / (60 * 24);
        assertTrue(maksimumKaymaGun <= AppointmentScheduleRules.MAX_ADVANCE_DAYS,
                "Kayma, planlama penceresini aşmamalı");

        System.out.printf("%n  Çözülen %d randevu · ortalama kayma %.1f saat · en fazla %.1f saat%n%n",
                cozulenler.size(),
                plan.olcum().ortalamaKaymaDakika() / 60.0,
                plan.olcum().maksimumKaymaDakika() / 60.0);
    }

    @Test
    @DisplayName("Uygulama: eski randevular iptal edilir ve rezervasyonlu öneri oluşur")
    void uygulamaOneriUretir() {
        DisruptionPlan plan = disruptionService.onizle(
                bozulanDoktor.getId(), bozulanGun, "Doktor acil ameliyata girdi", 0);

        disruptionService.uygula(plan);

        // Bozulan gündeki randevuların hepsi iptal edilmiş olmalı
        var kalanAktif = appointmentRepository
                .findByDoctorIdAndAppointmentDateBetween(bozulanDoktor.getId(),
                        bozulanGun.atStartOfDay(), bozulanGun.atTime(LocalTime.MAX))
                .stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .count();
        assertEquals(0, kalanAktif, "Bozulan gündeki tüm randevular iptal edilmeliydi");

        // Çözülenler için öneri oluşmuş olmalı
        long beklenenOneri = plan.kalemler().stream().filter(DisruptionPlan.Kalem::cozuldu).count();
        long olusanOneri = proposalRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProposalStatus.PENDING)
                .count();
        assertEquals(beklenenOneri, olusanOneri, "Her çözülen randevu için bir öneri oluşmalıydı");
    }

    @Test
    @DisplayName("R11: Rezerve edilen slot başka bir hastaya önerilmez")
    void rezerveSlotKorunur() {
        DisruptionPlan ilkPlan = disruptionService.onizle(
                bozulanDoktor.getId(), bozulanGun, "Birinci bozulma", 0);
        disruptionService.uygula(ilkPlan);

        var rezerveSlotlar = proposalRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProposalStatus.PENDING)
                .map(p -> p.getDoctor().getId() + "#" + p.getProposedDate())
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(rezerveSlotlar.isEmpty(), "Deney için en az bir rezervasyon gerekli");

        // Aynı doktorun başka bir günü de bozulsun; yeni öneriler rezerve slotları kullanmamalı
        // Komşu günler fikstürde doldurulduğu için boş bir gün seçilir
        LocalDate ikinciGun = bozulanGun.plusDays(5);
        User yeniHasta = hastaOlustur("ikinci-bozulma");
        randevuOlustur(yeniHasta, bozulanDoktor, ikinciGun.atTime(LocalTime.of(9, 0)));

        DisruptionPlan ikinciPlan = disruptionService.onizle(
                bozulanDoktor.getId(), ikinciGun, "İkinci bozulma", 0);

        for (var kalem : ikinciPlan.kalemler()) {
            if (!kalem.cozuldu()) continue;
            String anahtar = kalem.yeniDoktorId() + "#" + kalem.yeniTarih();
            assertFalse(rezerveSlotlar.contains(anahtar),
                    "Rezerve edilmiş slot ikinci kez önerilmiş: " + anahtar);
        }
    }

    // ================= FİKSTÜR =================

    private Doctor doktorOlustur(String etiket) {
        User u = new User();
        u.setEmail("bozulma-dr-" + etiket + "-" + System.nanoTime() + TEST_EPOSTA_SONU);
        u.setPassword("x");
        u.setFirstName("Dr");
        u.setLastName(etiket);
        u.setRole(Role.DOCTOR);
        u = userRepository.save(u);

        Doctor d = new Doctor();
        d.setUser(u);
        d.setDepartment(poliklinik);
        d.setTitle("Dr.");
        return doctorRepository.save(d);
    }

    private User hastaOlustur(String etiket) {
        User u = new User();
        u.setEmail("bozulma-hasta-" + etiket + "-" + System.nanoTime() + TEST_EPOSTA_SONU);
        u.setPassword("x");
        u.setFirstName("Hasta");
        u.setLastName(etiket);
        u.setRole(Role.PATIENT);
        return userRepository.save(u);
    }

    private void randevuOlustur(User hasta, Doctor doktor, LocalDateTime zaman) {
        Appointment a = new Appointment();
        a.setPatient(hasta);
        a.setDoctor(doktor);
        a.setAppointmentDate(zaman);
        a.setStatus(AppointmentStatus.PENDING);
        a.setCreatedAt(LocalDateTime.now().minusDays(5));
        appointmentRepository.save(a);
    }

    private List<LocalTime> uygunSaatler() {
        List<LocalTime> liste = new ArrayList<>();
        LocalTime t = AppointmentScheduleRules.WORK_START;
        while (!t.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES).isAfter(AppointmentScheduleRules.WORK_END)) {
            boolean ogle = t.isBefore(AppointmentScheduleRules.LUNCH_END)
                    && t.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES).isAfter(AppointmentScheduleRules.LUNCH_START);
            if (!ogle) liste.add(t);
            t = t.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES);
        }
        return liste;
    }

    private void temizle() {
        var testKullanicilari = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().endsWith(TEST_EPOSTA_SONU))
                .toList();

        // Öneriler randevulara bağlı: önce onlar silinmeli
        proposalRepository.findAll().stream()
                .filter(p -> p.getPatient() != null && p.getPatient().getEmail() != null
                        && p.getPatient().getEmail().endsWith(TEST_EPOSTA_SONU))
                .forEach(proposalRepository::delete);

        for (User u : testKullanicilari) {
            appointmentRepository.findByPatientId(u.getId()).forEach(appointmentRepository::delete);
            doctorRepository.findByUserId(u.getId()).ifPresent(d -> {
                appointmentRepository.findByDoctorId(d.getId()).forEach(appointmentRepository::delete);
                doctorRepository.delete(d);
            });
        }
        testKullanicilari.forEach(userRepository::delete);
        departmentRepository.findAll().stream()
                .filter(d -> d.getName() != null && d.getName().startsWith("Bozulma Polikliniği"))
                .forEach(departmentRepository::delete);
    }
}
