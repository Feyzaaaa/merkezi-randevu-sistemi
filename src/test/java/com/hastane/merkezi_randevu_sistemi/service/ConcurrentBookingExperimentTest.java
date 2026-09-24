package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EŞZAMANLI RANDEVU TALEBİ DENEYİ (yarış durumu / race condition)
 *
 * Tez iddiası: uygulama katmanındaki "önce kontrol et, sonra kaydet" mantığı tek
 * başına yeterli DEĞİLDİR. İki istek kontrolü aynı anda geçip ikisi de kaydedebilir.
 * Asıl garanti, veritabanındaki kısmi unique index'tedir.
 *
 * Bu sınıf iddiayı ölçer:
 *   DENEY A — index VARKEN  : N eşzamanlı istekten kaç tanesi kaydedilir?
 *   DENEY B — index YOKKEN  : aynı deney, yalnızca uygulama katmanı korumasıyla
 *
 * Deney B'nin sonucu doğası gereği olasılıksaldır (yarış durumu her çalıştırmada
 * aynı şekilde gerçekleşmez); bu yüzden B'de katı bir eşitlik iddia edilmez,
 * sonuç raporlanır. Deney A ise deterministiktir ve kesin olarak doğrulanır.
 *
 * NOT: Gerçek PostgreSQL gerektirir — kısmi unique index (WHERE koşullu) bir
 * PostgreSQL özelliğidir ve bellek içi veritabanlarında karşılığı yoktur.
 *
 * DİKKAT: Deney B, ölçüm yapabilmek için index'i GEÇİCİ olarak düşürür ve deney
 * bitince geri oluşturur. Test yarıda kesilirse index eksik kalabileceği için
 * hem kurulum (@BeforeEach) hem de toparlama (@AfterEach) adımlarında index'in
 * varlığı yeniden sağlanır; böylece bir sonraki çalıştırma hasarı onarır.
 */
@SpringBootTest
class ConcurrentBookingExperimentTest {

    /** Aynı randevu saatine aynı anda talep gönderecek istemci sayısı */
    private static final int ESZAMANLI_ISTEK = 20;

    private static final String DOKTOR_INDEX = "ux_appointments_doctor_slot_active";
    private static final String TEST_EPOSTA_DESENI = "deney-%d@test.local";

    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DataSource dataSource;

    // Deney sırasında gerçek e-posta gönderilmesin
    @MockitoBean private EmailService emailService;

    private Doctor doktor;
    private List<User> hastalar;
    private LocalDateTime hedefSaat;

    // --- Kurulum ---

    @BeforeEach
    void hazirla() {
        // Önce artıkları temizle, SONRA index'i garanti et: mükerrer satırlar
        // dururken unique index oluşturulamaz.
        temizle();
        indexOlusturVarsayilan();

        Department poliklinik = departmentRepository.save(new Department("Deney Polikliniği " + System.nanoTime()));

        User doktorKullanici = new User();
        doktorKullanici.setEmail("deney-doktor-" + System.nanoTime() + "@test.local");
        doktorKullanici.setPassword("x");
        doktorKullanici.setFirstName("Deney");
        doktorKullanici.setLastName("Doktoru");
        doktorKullanici.setRole(Role.DOCTOR);
        doktorKullanici = userRepository.save(doktorKullanici);

        doktor = new Doctor();
        doktor.setUser(doktorKullanici);
        doktor.setDepartment(poliklinik);
        doktor.setTitle("Uzm. Dr.");
        doktor = doctorRepository.save(doktor);

        // Her istemci FARKLI bir hasta: böylece hasta bazlı kurallar (aynı gün aynı
        // poliklinik, aktif randevu limiti) devreye girmez ve yalnızca DOKTOR slotu
        // üzerindeki yarış durumu ölçülür.
        hastalar = new ArrayList<>();
        for (int i = 0; i < ESZAMANLI_ISTEK; i++) {
            User hasta = new User();
            hasta.setEmail(String.format(TEST_EPOSTA_DESENI, i));
            hasta.setPassword("x");
            hasta.setFirstName("Deney");
            hasta.setLastName("Hasta " + i);
            hasta.setRole(Role.PATIENT);
            hastalar.add(userRepository.save(hasta));
        }

        // Tüm zaman kurallarına (R2–R6) uyan, kimsenin kullanmadığı bir dilim
        hedefSaat = LocalDateTime.now().plusDays(20)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    @AfterEach
    void toparla() {
        // SIRA ÖNEMLİ: deney B mükerrer kayıtlar üretmiş olabilir; bunlar silinmeden
        // unique index yeniden oluşturulamaz.
        temizle();
        indexOlusturVarsayilan();
    }

    private void temizle() {
        List<User> testKullanicilari = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().endsWith("@test.local"))
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
                .filter(d -> d.getName() != null && d.getName().startsWith("Deney Polikliniği"))
                .forEach(departmentRepository::delete);
    }

    // --- Deney motoru ---

    /**
     * ESZAMANLI_ISTEK kadar istemci, hepsi aynı anda serbest bırakılarak aynı
     * randevu saatine talep gönderir.
     *
     * @return kaç talebin kaydedildiği
     */
    private int eszamanliTalepGonder() throws InterruptedException {
        ExecutorService havuz = Executors.newFixedThreadPool(ESZAMANLI_ISTEK);
        CountDownLatch baslangicKapisi = new CountDownLatch(1);
        CountDownLatch bitis = new CountDownLatch(ESZAMANLI_ISTEK);
        AtomicInteger basarili = new AtomicInteger();

        for (User hasta : hastalar) {
            havuz.submit(() -> {
                try {
                    // Tüm thread'ler burada bekler; kapı açılınca hepsi aynı anda başlar
                    baslangicKapisi.await();

                    Appointment talep = new Appointment();
                    User p = new User();
                    p.setId(hasta.getId());
                    talep.setPatient(p);

                    Doctor d = new Doctor();
                    d.setId(doktor.getId());
                    talep.setDoctor(d);

                    talep.setAppointmentDate(hedefSaat);

                    appointmentService.createAppointment(talep);
                    basarili.incrementAndGet();
                } catch (Exception e) {
                    // Beklenen: çakışma nedeniyle reddedilme
                } finally {
                    bitis.countDown();
                }
            });
        }

        baslangicKapisi.countDown();          // yarış başlasın
        bitis.await(60, TimeUnit.SECONDS);
        havuz.shutdownNow();
        return basarili.get();
    }

    private long kaydedilenRandevuSayisi() {
        return appointmentRepository.findByDoctorId(doktor.getId()).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .filter(a -> hedefSaat.equals(a.getAppointmentDate()))
                .count();
    }

    /** Deneyde bu doktor için oluşan tüm randevuları siler (index'i geri koyabilmek için) */
    private void deneyRandevulariniSil() {
        appointmentRepository.findByDoctorId(doktor.getId()).forEach(appointmentRepository::delete);
    }

    private void indexDusur() {
        calistir("DROP INDEX IF EXISTS " + DOKTOR_INDEX);
    }

    private void indexOlusturVarsayilan() {
        calistir("CREATE UNIQUE INDEX IF NOT EXISTS " + DOKTOR_INDEX
                + " ON appointments (doctor_id, appointment_date) WHERE status <> 'CANCELLED'");
    }

    private void calistir(String sql) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Deney kurulumu başarısız: " + sql, e);
        }
    }

    // --- DENEYLER ---

    @Test
    @DisplayName("DENEY A: Kısmi unique index varken, eşzamanlı N talepten yalnızca 1'i kaydedilir")
    void indexVarkenTekKayit() throws InterruptedException {
        indexOlusturVarsayilan();

        int basarili = eszamanliTalepGonder();
        long kayitli = kaydedilenRandevuSayisi();

        System.out.println("""

                ┌─ DENEY A — Veritabanı garantisi AÇIK ────────────────────┐
                │ Eşzamanlı talep      : %2d
                │ Kabul edilen         : %2d
                │ Reddedilen           : %2d
                │ Veritabanındaki kayıt: %2d
                └──────────────────────────────────────────────────────────┘
                """.formatted(ESZAMANLI_ISTEK, basarili, ESZAMANLI_ISTEK - basarili, kayitli));

        assertEquals(1, kayitli,
                "Aynı doktor/saat için veritabanında tam olarak 1 aktif randevu olmalıydı");
        assertEquals(1, basarili,
                "Taleplerden yalnızca biri kabul edilmeliydi");
    }

    @Test
    @DisplayName("DENEY B: Index olmadan, yalnızca uygulama katmanı koruması — mükerrer kayıt riski ölçülür")
    void indexYokkenMukerrerRisk() throws InterruptedException {
        indexDusur();
        try {
            int basarili = eszamanliTalepGonder();
            long kayitli = kaydedilenRandevuSayisi();

            System.out.println("""

                    ┌─ DENEY B — Veritabanı garantisi KAPALI ──────────────────┐
                    │ Eşzamanlı talep      : %2d
                    │ Kabul edilen         : %2d
                    │ Veritabanındaki kayıt: %2d
                    │ MÜKERRER KAYIT       : %2d
                    └──────────────────────────────────────────────────────────┘
                    %s
                    """.formatted(ESZAMANLI_ISTEK, basarili, kayitli, Math.max(kayitli - 1, 0),
                    kayitli > 1
                            ? "SONUÇ: Uygulama katmanı kontrolü yarış durumunu engelleyemedi."
                            : "SONUÇ: Bu çalıştırmada yarış penceresi yakalanmadı (olasılıksal)."));

            // Yarış durumu olasılıksaldır: her çalıştırmada mükerrer kayıt oluşmayabilir.
            // Bu yüzden katı eşitlik iddia edilmez; en az bir kaydın oluştuğu doğrulanır.
            assertTrue(kayitli >= 1, "En az bir randevu kaydedilmeliydi");
        } finally {
            // Mükerrer kayıtlar silinmeden index geri oluşturulamaz
            deneyRandevulariniSil();
            indexOlusturVarsayilan();
        }
    }
}
