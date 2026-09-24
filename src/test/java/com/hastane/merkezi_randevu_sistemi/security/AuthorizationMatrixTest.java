package com.hastane.merkezi_randevu_sistemi.security;

import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.model.Role;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.service.EmailService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YETKİ MATRİSİNİN UÇTAN UCA DOĞRULANMASI
 *
 * README'deki yetki matrisi elle yazılmış bir belgedir; bu test onun koddaki
 * karşılığını ölçer. Her uç nokta, dört "aktör" ile denenir:
 *
 *     kimliksiz (token yok) · HASTA · DOKTOR · YÖNETİCİ
 *
 * ÖLÇÜLEN ŞEY YETKİLENDİRMEDİR, İŞ SONUCU DEĞİL:
 *   - Reddedilmesi beklenen hücrede durum kodu tam olarak 401 veya 403 olmalı.
 *   - İzin verilmesi beklenen hücrede kod 401/403 OLMAMALI; 200, 400 veya 404
 *     fark etmez — istek yetki katmanını geçmiş demektir. (Örneğin var olmayan
 *     bir randevu kimliğiyle yapılan istek 400 döner; bu bir yetki reddi değildir.)
 *
 * Sahiplik kontrolü olan uçlarda gerçek kullanıcılar kullanılır: sentetik bir
 * doktor kimliğiyle yapılan istek sahiplik kontrolünden 403 alır ve matris
 * yanlış görünürdü.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationMatrixTest {

    /** Var olmayan kayıt kimliği: yetki geçilirse iş katmanı 400/404 döner */
    private static final long YOK_ID = 999_999L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;

    @MockitoBean private EmailService emailService;

    /** Aktör: istek hangi kimlikle gönderiliyor (null token = kimliksiz) */
    private record Aktor(String ad, String token) {}

    /** Matrisin bir satırı: uç nokta ve hangi rollere açık olduğu */
    private record UcNokta(String ad, HttpMethod method, String path, String body, List<String> izinliAktorler) {}

    private Aktor kimliksiz, hasta, doktor, yonetici;
    private List<Aktor> aktorler;
    private final Map<String, Integer> sonuclar = new LinkedHashMap<>();
    private int gecen = 0, kalan = 0;

    @BeforeAll
    void hazirla() {
        User yoneticiKullanici = kullaniciBul(Role.ADMIN);
        User doktorKullanici = kullaniciBul(Role.DOCTOR);
        User hastaKullanici = hastaHazirla();

        kimliksiz = new Aktor("kimliksiz", null);
        hasta = new Aktor("HASTA", jwtUtil.generateToken(hastaKullanici));
        doktor = new Aktor("DOKTOR", jwtUtil.generateToken(doktorKullanici));
        yonetici = new Aktor("YÖNETİCİ", jwtUtil.generateToken(yoneticiKullanici));
        aktorler = List.of(kimliksiz, hasta, doktor, yonetici);

        this.hastaId = hastaKullanici.getId();
        this.doktorKullaniciId = doktorKullanici.getId();
        this.yoneticiId = yoneticiKullanici.getId();
        this.doktorId = doctorRepository.findByUserId(doktorKullanici.getId())
                .map(Doctor::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Örnek veride doktor kaydı bulunamadı; data.sql yüklenmiş olmalı"));
    }

    private Long hastaId, doktorKullaniciId, doktorId, yoneticiId;

    private User kullaniciBul(Role rol) {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == rol)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        rol + " rolünde kullanıcı yok; data.sql yüklenmiş olmalı"));
    }

    /** Matris testi için hasta hesabı: varsa mevcut hasta kullanılır, yoksa oluşturulur */
    private User hastaHazirla() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.PATIENT)
                .findFirst()
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("matris-hasta@test.local");
                    u.setPassword("x");
                    u.setFirstName("Matris");
                    u.setLastName("Hasta");
                    u.setRole(Role.PATIENT);
                    return userRepository.save(u);
                });
    }

    // --- MATRİS TANIMI ---

    private List<UcNokta> matris() {
        String yarin = LocalDate.now().plusDays(1).toString();
        String gecmisTarihliRandevu = """
                {"patient":{"id":%d},"doctor":{"id":%d},"appointmentDate":"2020-01-15T10:00:00"}
                """.formatted(hastaId, doktorId);

        return List.of(
            // --- HERKESE AÇIK ---
            new UcNokta("POST /users/register", HttpMethod.POST, "/api/users/register",
                    "{\"email\":\"\",\"password\":\"x\"}", List.of("kimliksiz", "HASTA", "DOKTOR", "YÖNETİCİ")),
            // NOT: Giriş ucu, hatalı kimlik bilgisinde İŞ KURALI gereği 401 döner.
            // Bu test yetkilendirmeyi ölçtüğü için ikisinin karışmaması adına eksik
            // gövde gönderilir; uç nokta 400 döner ve "yetki katmanı geçildi" net olur.
            new UcNokta("POST /users/login", HttpMethod.POST, "/api/users/login",
                    "{}", List.of("kimliksiz", "HASTA", "DOKTOR", "YÖNETİCİ")),

            // --- KİMLİĞİ DOĞRULANMIŞ HERKES ---
            new UcNokta("GET /departments", HttpMethod.GET, "/api/departments",
                    null, List.of("HASTA", "DOKTOR", "YÖNETİCİ")),
            new UcNokta("GET /doctors", HttpMethod.GET, "/api/doctors",
                    null, List.of("HASTA", "DOKTOR", "YÖNETİCİ")),
            new UcNokta("GET /appointments/available-slots", HttpMethod.GET,
                    "/api/appointments/available-slots?doctorId=" + doktorId + "&date=" + yarin,
                    null, List.of("HASTA", "DOKTOR", "YÖNETİCİ")),
            new UcNokta("PATCH /users/me/password", HttpMethod.PATCH, "/api/users/me/password",
                    "{\"currentPassword\":\"yanlis\",\"newPassword\":\"YeniSifre2026\"}",
                    List.of("HASTA", "DOKTOR", "YÖNETİCİ")),

            // --- YALNIZCA YÖNETİCİ ---
            new UcNokta("GET /users (tüm kullanıcılar)", HttpMethod.GET, "/api/users",
                    null, List.of("YÖNETİCİ")),
            new UcNokta("GET /appointments (tüm randevular)", HttpMethod.GET, "/api/appointments",
                    null, List.of("YÖNETİCİ")),
            new UcNokta("POST /doctors", HttpMethod.POST, "/api/doctors",
                    "{}", List.of("YÖNETİCİ")),
            new UcNokta("POST /departments", HttpMethod.POST, "/api/departments",
                    "{\"name\":\"Dahiliye\"}", List.of("YÖNETİCİ")),
            new UcNokta("GET /admin/stats", HttpMethod.GET, "/api/admin/stats",
                    null, List.of("YÖNETİCİ")),
            new UcNokta("GET /admin/users", HttpMethod.GET, "/api/admin/users",
                    null, List.of("YÖNETİCİ")),
            new UcNokta("GET /admin/appointments", HttpMethod.GET, "/api/admin/appointments",
                    null, List.of("YÖNETİCİ")),
            new UcNokta("GET /admin/audit-logs", HttpMethod.GET, "/api/admin/audit-logs",
                    null, List.of("YÖNETİCİ")),
            new UcNokta("PATCH /admin/users/{id}/role", HttpMethod.PATCH,
                    "/api/admin/users/" + yoneticiId + "/role",
                    "{\"role\":\"PATIENT\"}", List.of("YÖNETİCİ")),
            new UcNokta("POST /admin/doctors", HttpMethod.POST, "/api/admin/doctors",
                    "{}", List.of("YÖNETİCİ")),
            new UcNokta("POST /admin/departments", HttpMethod.POST, "/api/admin/departments",
                    "{\"name\":\"Dahiliye\"}", List.of("YÖNETİCİ")),

            // --- YALNIZCA DOKTOR ---
            new UcNokta("GET /doctors/by-user/{id}", HttpMethod.GET,
                    "/api/doctors/by-user/" + doktorKullaniciId, null, List.of("DOKTOR")),
            new UcNokta("GET /appointments/doctor/{id}", HttpMethod.GET,
                    "/api/appointments/doctor/" + doktorId, null, List.of("DOKTOR")),
            new UcNokta("PATCH /appointments/{id}/note", HttpMethod.PATCH,
                    "/api/appointments/" + YOK_ID + "/note", "{\"note\":\"x\"}", List.of("DOKTOR")),
            new UcNokta("PATCH /appointments/{id}/confirm", HttpMethod.PATCH,
                    "/api/appointments/" + YOK_ID + "/confirm", null, List.of("DOKTOR")),
            new UcNokta("PATCH /appointments/{id}/complete", HttpMethod.PATCH,
                    "/api/appointments/" + YOK_ID + "/complete", null, List.of("DOKTOR")),
            new UcNokta("GET /doctors/{id}/leaves", HttpMethod.GET,
                    "/api/doctors/" + doktorId + "/leaves", null, List.of("DOKTOR")),
            new UcNokta("POST /doctors/{id}/leaves", HttpMethod.POST,
                    "/api/doctors/" + doktorId + "/leaves", "{\"leaveDate\":\"2020-01-01\"}", List.of("DOKTOR")),
            new UcNokta("POST /lab-results", HttpMethod.POST, "/api/lab-results",
                    "{}", List.of("DOKTOR")),

            // --- YALNIZCA HASTA ---
            new UcNokta("POST /appointments", HttpMethod.POST, "/api/appointments",
                    gecmisTarihliRandevu, List.of("HASTA")),
            new UcNokta("GET /appointments/patient/{id}", HttpMethod.GET,
                    "/api/appointments/patient/" + hastaId, null, List.of("HASTA")),
            new UcNokta("GET /patient-profiles/by-user/{id}", HttpMethod.GET,
                    "/api/patient-profiles/by-user/" + hastaId, null, List.of("HASTA")),

            // --- PAYLAŞILAN YETKİLER ---
            new UcNokta("GET /lab-results/patient/{id}", HttpMethod.GET,
                    "/api/lab-results/patient/" + hastaId, null, List.of("HASTA", "DOKTOR")),
            new UcNokta("PATCH /appointments/{id}/cancel", HttpMethod.PATCH,
                    "/api/appointments/" + YOK_ID + "/cancel", null, List.of("HASTA", "DOKTOR", "YÖNETİCİ"))
        );
    }

    // --- TEST ÜRETİMİ ---

    @TestFactory
    @DisplayName("Yetki matrisi: her uç nokta × her rol")
    Stream<DynamicTest> yetkiMatrisi() {
        List<DynamicTest> testler = new ArrayList<>();

        for (UcNokta uc : matris()) {
            for (Aktor aktor : aktorler) {
                boolean izinliOlmali = uc.izinliAktorler().contains(aktor.ad());
                String baslik = "%-38s | %-10s -> %s".formatted(
                        uc.ad(), aktor.ad(), izinliOlmali ? "İZİN" : "RET");

                testler.add(DynamicTest.dynamicTest(baslik, () -> {
                    int kod = istekGonder(uc, aktor);
                    sonuclar.put(uc.ad() + "|" + aktor.ad(), kod);

                    if (izinliOlmali) {
                        // Yetki katmanı geçilmeli; iş katmanının ne döndüğü bu testin konusu değil
                        assertTrue(kod != 401 && kod != 403,
                                () -> "%s → %s: yetki katmanını geçmeliydi ama %d döndü"
                                        .formatted(aktor.ad(), uc.ad(), kod));
                        gecen++;
                    } else {
                        int beklenen = aktor.token() == null ? 401 : 403;
                        assertEquals(beklenen, kod,
                                () -> "%s → %s: %d beklenirken %d döndü"
                                        .formatted(aktor.ad(), uc.ad(), beklenen, kod));
                        kalan++;
                    }
                }));
            }
        }
        return testler.stream();
    }

    private int istekGonder(UcNokta uc, Aktor aktor) throws Exception {
        MockHttpServletRequestBuilder istek = switch (uc.method().name()) {
            case "GET" -> MockMvcRequestBuilders.get(uc.path());
            case "POST" -> MockMvcRequestBuilders.post(uc.path());
            case "PATCH" -> MockMvcRequestBuilders.patch(uc.path());
            case "DELETE" -> MockMvcRequestBuilders.delete(uc.path());
            default -> throw new IllegalArgumentException("Desteklenmeyen metot: " + uc.method());
        };

        if (aktor.token() != null) {
            istek = istek.header("Authorization", "Bearer " + aktor.token());
        }
        if (uc.body() != null) {
            istek = istek.contentType(MediaType.APPLICATION_JSON).content(uc.body());
        }

        MvcResult sonuc = mockMvc.perform(istek).andReturn();
        return sonuc.getResponse().getStatus();
    }

    @AfterAll
    void matrisiYazdir() {
        StringBuilder sb = new StringBuilder("\n\n  YETKİ MATRİSİ — ölçülen HTTP durum kodları\n");
        sb.append("  ").append("─".repeat(84)).append("\n");
        sb.append("  %-38s %9s %9s %9s %9s%n".formatted(
                "UÇ NOKTA", "kimliksiz", "HASTA", "DOKTOR", "YÖNETİCİ"));
        sb.append("  ").append("─".repeat(84)).append("\n");

        for (UcNokta uc : matris()) {
            sb.append("  %-38s".formatted(uc.ad()));
            for (Aktor aktor : aktorler) {
                Integer kod = sonuclar.get(uc.ad() + "|" + aktor.ad());
                boolean izinli = uc.izinliAktorler().contains(aktor.ad());
                // Yetki geçildiyse işaret, reddedildiyse kodun kendisi gösterilir
                String hucre = kod == null ? "-" : (izinli ? "✓ " + kod : String.valueOf(kod));
                sb.append(" %9s".formatted(hucre));
            }
            sb.append("\n");
        }
        sb.append("  ").append("─".repeat(84)).append("\n");
        sb.append("  ✓ = yetki katmanı geçildi (kod iş katmanından gelir)   401 = kimlik yok   403 = yetki yok\n");
        sb.append("  Toplam kontrol: %d  (izin verilen: %d, reddedilen: %d)%n"
                .formatted(gecen + kalan, gecen, kalan));
        System.out.println(sb);

        userRepository.findAll().stream()
                .filter(u -> "matris-hasta@test.local".equals(u.getEmail()))
                .forEach(userRepository::delete);
    }
}
