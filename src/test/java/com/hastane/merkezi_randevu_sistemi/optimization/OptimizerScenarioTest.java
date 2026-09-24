package com.hastane.merkezi_randevu_sistemi.optimization;

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
 * OPTİMİZASYON MOTORU — SENARYO DENEYİ
 *
 * Boş bir sistemde tüm adayların maliyeti birbirine eşittir ve motorun ayrım
 * yaptığı görülemez. Bu deney, kontrollü bir yük oluşturup motorun beklenen
 * tercihi yapıp yapmadığını ölçer.
 *
 * KURULUM — aynı poliklinikte üç doktor, yarın için farklı doluluklarda:
 *     Doktor A : ~%95 dolu   (aşırı yüklü — mola/gecikme payı kalmamış)
 *     Doktor B : ~%85 dolu   (HEDEF doluluk)
 *     Doktor C :   %0 dolu   (atıl kapasite)
 *
 * BEKLENEN: Motor, yarın için Doktor B'yi tercih etmelidir. Ne tamamen boş günü
 * (israf) ne de tıka basa dolu günü (kırılgan plan) seçmelidir.
 *
 * Bu, tezin "%100 doluluk her zaman iyi değildir" savının ölçülmüş karşılığıdır.
 */
@SpringBootTest
class OptimizerScenarioTest {

    private static final String TEST_EPOSTA_SONU = "@senaryo.local";

    @Autowired private AppointmentOptimizer optimizer;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private OptimizationWeights agirliklar;

    @MockitoBean private EmailService emailService;

    private Department poliklinik;
    private Doctor doktorA, doktorB, doktorC;
    private User yeniHasta;
    private LocalDate yarin;

    @BeforeEach
    void hazirla() {
        temizle();
        yarin = LocalDate.now().plusDays(1);

        poliklinik = departmentRepository.save(new Department("Senaryo Polikliniği " + System.nanoTime()));
        doktorA = doktorOlustur("A");
        doktorB = doktorOlustur("B");
        doktorC = doktorOlustur("C");
        yeniHasta = hastaOlustur("yeni");

        int slotSayisi = gunlukSlotSayisi();
        doldur(doktorA, (int) Math.round(slotSayisi * 0.95));   // aşırı yüklü
        doldur(doktorB, (int) Math.round(slotSayisi * 0.85));   // hedefte
        // doktorC boş bırakılır
    }

    @AfterEach
    void toparla() {
        temizle();
    }

    @Test
    @DisplayName("Motor, hedef dolulukta olan doktoru tercih eder (ne boş ne tıka basa dolu)")
    void hedefDoluluktakiDoktorTercihEdilir() {
        var sonuc = optimizer.onerileriHesapla(yeniHasta.getId(), poliklinik.getId());

        assertFalse(sonuc.oneriler().isEmpty(), "En az bir öneri üretilmeliydi");

        // Yarın için her doktorun en iyi adayını bul
        var yarinkiler = sonuc.oneriler().stream()
                .filter(o -> o.tarih().toLocalDate().equals(yarin))
                .toList();

        rapor(sonuc);

        SlotScore enIyi = sonuc.oneriler().get(0);
        assertEquals(yarin, enIyi.tarih().toLocalDate(),
                "En iyi öneri yarın olmalıydı (bekleme maliyeti en düşük gün)");
        assertEquals(doktorB.getId(), enIyi.doctorId(),
                "Hedef dolulukta olan Doktor B tercih edilmeliydi; seçilen doktor: " + enIyi.doctorAdi());
        assertFalse(yarinkiler.isEmpty());
    }

    @Test
    @DisplayName("Doluluk maliyeti sıralaması: hedefteki gün < boş gün < aşırı yüklü gün")
    void dolulukMaliyetiSiralamasi() {
        double hedefte = optimizer.dolulukMaliyetiHesapla(agirliklar.getHedefDoluluk());
        double bos = optimizer.dolulukMaliyetiHesapla(0.05);
        double asiriYuklu = optimizer.dolulukMaliyetiHesapla(0.98);

        System.out.println("""

                ┌─ DOLULUK MALİYETİ ───────────────────────────────┐
                │ Hedefte (%%%2.0f)   : %.3f  ← tercih edilen
                │ Neredeyse boş    : %.3f
                │ Aşırı yüklü      : %.3f  ← en ağır ceza
                └──────────────────────────────────────────────────┘
                """.formatted(agirliklar.getHedefDoluluk() * 100, hedefte, bos, asiriYuklu));

        assertTrue(hedefte < bos, "Hedefteki gün, boş günden iyi olmalı");
        assertTrue(bos < asiriYuklu, "Boş gün, aşırı yüklü günden iyi olmalı");
    }

    @Test
    @DisplayName("Öneriler çeşitlidir: aynı doktorun aynı gününden birden fazla öneri verilmez")
    void onerilerCesitlidir() {
        var sonuc = optimizer.onerileriHesapla(yeniHasta.getId(), poliklinik.getId());

        var anahtarlar = sonuc.oneriler().stream()
                .map(o -> o.doctorId() + "#" + o.tarih().toLocalDate())
                .toList();

        assertEquals(anahtarlar.size(), anahtarlar.stream().distinct().count(),
                "Her (doktor, gün) çiftinden en fazla bir öneri olmalı: " + anahtarlar);
    }

    @Test
    @DisplayName("Riskli hasta da randevu alır: yüksek iptal riski adayları elemez")
    void riskliHastaDaRandevuAlir() {
        // Geçmişinde çok iptal olan bir hasta oluştur
        User riskli = hastaOlustur("riskli");
        for (int i = 0; i < 6; i++) {
            Appointment gecmis = new Appointment();
            gecmis.setPatient(riskli);
            gecmis.setDoctor(doktorC);
            gecmis.setCreatedAt(LocalDateTime.now().minusDays(40));
            // Saat ve dakika ayrı ayrı ilerletilir: withMinute(60) geçersizdir
            gecmis.setAppointmentDate(LocalDateTime.now().minusDays(10)
                    .withHour(9 + i / 4).withMinute((i % 4) * 15).withSecond(0).withNano(0));
            gecmis.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(gecmis);
        }

        var sonuc = optimizer.onerileriHesapla(riskli.getId(), poliklinik.getId());

        assertFalse(sonuc.oneriler().isEmpty(),
                "İptal riski yüksek olsa bile hastaya randevu önerilmeli — risk yalnızca sıralamayı etkiler");
        System.out.printf("%n  Riskli hasta için üretilen öneri sayısı: %d (iptal olasılığı %%%.1f)%n%n",
                sonuc.oneriler().size(), sonuc.oneriler().get(0).iptalOlasiligi() * 100);
    }

    // --- rapor ---

    private void rapor(AppointmentOptimizer.Sonuc sonuc) {
        StringBuilder sb = new StringBuilder("\n\n  OPTİMİZASYON SENARYOSU — yarın için doluluk durumu\n");
        sb.append("  Doktor A: ~%%95 dolu · Doktor B: ~%%85 dolu (hedef) · Doktor C: boş%n".formatted());
        sb.append("  ").append("─".repeat(78)).append("\n");
        sb.append("  %-4s %-16s %-22s %8s %8s%n".formatted("#", "TARİH/SAAT", "DOKTOR", "DOLULUK", "MALİYET"));
        sb.append("  ").append("─".repeat(78)).append("\n");

        int i = 1;
        for (SlotScore o : sonuc.oneriler()) {
            sb.append("  %-4d %-16s %-22s %7.0f%% %8.4f%n".formatted(
                    i++,
                    o.tarih().toLocalDate() + " " + o.tarih().toLocalTime(),
                    o.doctorAdi(),
                    o.gunDolulugu() * 100,
                    o.toplamMaliyet()));
        }
        sb.append("  ").append("─".repeat(78)).append("\n");
        sb.append("  Değerlendirilen aday: %d · İptal modeli: %s%n"
                .formatted(sonuc.degerlendirilenAday(), sonuc.modelDurumu()));
        System.out.println(sb);
    }

    // --- fikstür yardımcıları ---

    private Doctor doktorOlustur(String etiket) {
        User u = new User();
        u.setEmail("senaryo-doktor-" + etiket + "-" + System.nanoTime() + TEST_EPOSTA_SONU);
        u.setPassword("x");
        u.setFirstName("Doktor");
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
        u.setEmail("senaryo-hasta-" + etiket + "-" + System.nanoTime() + TEST_EPOSTA_SONU);
        u.setPassword("x");
        u.setFirstName("Hasta");
        u.setLastName(etiket);
        u.setRole(Role.PATIENT);
        return userRepository.save(u);
    }

    /**
     * Doktorun yarınki gününü belirtilen sayıda randevuyla doldurur.
     * Kurallar atlanarak doğrudan kaydedilir: burada amaç kural denemek değil,
     * belirli bir doluluk durumu kurmaktır.
     */
    private void doldur(Doctor doktor, int adet) {
        User dolguHasta = hastaOlustur("dolgu-" + doktor.getId());
        List<LocalTime> saatler = uygunSaatler();

        for (int i = 0; i < Math.min(adet, saatler.size()); i++) {
            Appointment a = new Appointment();
            a.setPatient(dolguHasta);
            a.setDoctor(doktor);
            a.setAppointmentDate(yarin.atTime(saatler.get(i)));
            a.setStatus(AppointmentStatus.PENDING);
            a.setCreatedAt(LocalDateTime.now());
            appointmentRepository.save(a);
        }
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

    private int gunlukSlotSayisi() {
        return uygunSaatler().size();
    }

    private void temizle() {
        var testKullanicilari = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().endsWith(TEST_EPOSTA_SONU))
                .toList();
        for (User u : testKullanicilari) {
            appointmentRepository.findByPatientId(u.getId()).forEach(appointmentRepository::delete);
            doctorRepository.findByUserId(u.getId()).ifPresent(d -> {
                appointmentRepository.findByDoctorId(d.getId()).forEach(appointmentRepository::delete);
                doctorRepository.delete(d);
            });
        }
        testKullanicilari.forEach(userRepository::delete);
        departmentRepository.findAll().stream()
                .filter(d -> d.getName() != null && d.getName().startsWith("Senaryo Polikliniği"))
                .forEach(departmentRepository::delete);
    }
}
