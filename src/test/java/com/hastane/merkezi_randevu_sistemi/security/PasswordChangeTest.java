package com.hastane.merkezi_randevu_sistemi.security;

import com.hastane.merkezi_randevu_sistemi.config.SecurityConfig;
import com.hastane.merkezi_randevu_sistemi.controller.UserController;
import com.hastane.merkezi_randevu_sistemi.model.Role;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.service.AuditService;
import com.hastane.merkezi_randevu_sistemi.service.LoginAttemptService;
import com.hastane.merkezi_randevu_sistemi.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ŞİFRE DEĞİŞTİRME VE TOKEN GEÇERSİZLEŞTİRME TESTİ
 *
 * JWT durumsuzdur: üretildikten sonra sunucuda saklanmadığı için tek tek iptal
 * edilemez. Bu testler, şifre değişikliği damgasıyla eski token'ların nasıl
 * geçersiz kılındığını ve değiştirme kurallarının işlediğini doğrular.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "mhrs.jwt.secret=test-ortami-icin-en-az-32-byte-uzunlugunda-imzalama-anahtari",
        "mhrs.jwt.expiration-ms=3600000"
})
class PasswordChangeTest {

    private static final Long KULLANICI_ID = 7L;
    private static final String MEVCUT_SIFRE = "MevcutSifre2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private UserService userService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private AuditService auditService;
    @MockitoBean private LoginAttemptService loginAttemptService;

    private User kullanici;
    private String token;

    @BeforeEach
    void hazirla() {
        kullanici = new User();
        kullanici.setId(KULLANICI_ID);
        kullanici.setEmail("hasta@test.com");
        kullanici.setRole(Role.PATIENT);
        kullanici.setPassword(passwordEncoder.encode(MEVCUT_SIFRE));

        token = jwtUtil.generateToken(kullanici);
        when(userRepository.findById(KULLANICI_ID)).thenReturn(Optional.of(kullanici));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private org.springframework.test.web.servlet.ResultActions degistir(String mevcut, String yeni) throws Exception {
        return mockMvc.perform(patch("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + mevcut + "\",\"newPassword\":\"" + yeni + "\"}"));
    }

    @Test
    @DisplayName("Token'sız şifre değiştirme isteği 401 döner")
    void tokensizIstekReddedilir() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mevcut şifre yanlışsa değişiklik reddedilir ve denetim kaydına düşer")
    void mevcutSifreYanlissaReddedilir() throws Exception {
        degistir("YanlisSifre123", "YeniSifre2026")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mevcut şifreniz hatalı")));

        verify(userRepository, never()).save(any());
        verify(auditService).record(any(), eq(com.hastane.merkezi_randevu_sistemi.model.AuditAction.PASSWORD_CHANGE_FAILED),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("Yeni şifre politikaya uymuyorsa reddedilir")
    void zayifYeniSifreReddedilir() throws Exception {
        degistir(MEVCUT_SIFRE, "123")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("en az 8 karakter")));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Yeni şifre mevcut şifreyle aynı olamaz")
    void ayniSifreReddedilir() throws Exception {
        degistir(MEVCUT_SIFRE, MEVCUT_SIFRE)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aynı olamaz")));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Geçerli değişiklikte şifre güncellenir, damga atılır ve kilit sayacı sıfırlanır")
    void gecerliDegisiklikUygulanir() throws Exception {
        kullanici.setFailedLoginAttempts(3);

        degistir(MEVCUT_SIFRE, "YepyeniSifre2026").andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(
                passwordEncoder.matches("YepyeniSifre2026", kullanici.getPassword()),
                "Yeni şifre hash'lenerek kaydedilmeliydi");
        org.junit.jupiter.api.Assertions.assertNotNull(kullanici.getPasswordChangedAt(),
                "Token geçersizleştirme damgası atılmalıydı");
        org.junit.jupiter.api.Assertions.assertEquals(0, kullanici.getFailedLoginAttempts());
        verify(auditService).record(any(), eq(com.hastane.merkezi_randevu_sistemi.model.AuditAction.PASSWORD_CHANGED),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("TOKEN GEÇERSİZLEŞTİRME: şifre değişikliğinden önce üretilmiş token reddedilir")
    void eskiTokenReddedilir() throws Exception {
        // Token az önce üretildi; şifre ise "şimdi" değiştirilmiş sayılıyor
        kullanici.setPasswordChangedAt(LocalDateTime.now().plusMinutes(1));

        degistir(MEVCUT_SIFRE, "YepyeniSifre2026")
                .andExpect(status().isUnauthorized());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Şifre hiç değiştirilmemişse (damga yok) token normal çalışır")
    void damgaYoksaTokenGecerli() throws Exception {
        kullanici.setPasswordChangedAt(null);

        degistir(MEVCUT_SIFRE, "YepyeniSifre2026").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Damgadan SONRA üretilmiş token geçerlidir (yeni oturum çalışır)")
    void damgadanSonrakiTokenGecerli() throws Exception {
        kullanici.setPasswordChangedAt(LocalDateTime.now().minusMinutes(5));
        // Token damgadan sonra üretildiği için kabul edilmeli
        degistir(MEVCUT_SIFRE, "YepyeniSifre2026").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Şifre değiştirme her rol için açıktır (doktor da kendi şifresini değiştirebilir)")
    void herRolKendiSifresiniDegistirebilir() throws Exception {
        kullanici.setRole(Role.DOCTOR);
        token = jwtUtil.generateToken(kullanici);
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(kullanici));

        degistir(MEVCUT_SIFRE, "DoktorYeni2026").andExpect(status().isOk());
    }
}
