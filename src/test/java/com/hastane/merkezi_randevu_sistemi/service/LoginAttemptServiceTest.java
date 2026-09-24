package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KABA KUVVET KORUMASI TESTİ
 *
 * Ardışık başarısız denemelerin sayılması, eşikte kilitlenme ve sürenin
 * dolmasıyla kilidin kendiliğinden açılması senaryolarını kapsar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private LoginAttemptService loginAttemptService;

    private User kullanici;

    @BeforeEach
    void hazirla() {
        kullanici = new User();
        kullanici.setId(1L);
        kullanici.setEmail("hasta@test.com");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Yeni hesap kilitli değildir")
    void yeniHesapKilitliDegil() {
        assertFalse(loginAttemptService.isLocked(kullanici));
        assertEquals(LoginAttemptService.MAX_FAILED_ATTEMPTS, loginAttemptService.remainingAttempts(kullanici));
    }

    @Test
    @DisplayName("Eşiğin altındaki denemeler hesabı kilitlemez")
    void esiginAltiKilitlemez() {
        for (int i = 1; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
            assertFalse(loginAttemptService.recordFailure(kullanici),
                    i + ". denemede kilitlenmemeliydi");
        }
        assertFalse(loginAttemptService.isLocked(kullanici));
        assertEquals(1, loginAttemptService.remainingAttempts(kullanici));
    }

    @Test
    @DisplayName("Eşiğe ulaşan deneme hesabı kilitler")
    void esiktekiDenemeKilitler() {
        boolean kilitlendi = false;
        for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
            kilitlendi = loginAttemptService.recordFailure(kullanici);
        }

        assertTrue(kilitlendi, "Eşiğe ulaşıldığında kilitlenmeliydi");
        assertTrue(loginAttemptService.isLocked(kullanici));
        assertNotNull(kullanici.getLockedUntil());
        assertEquals(0, loginAttemptService.remainingAttempts(kullanici));
    }

    @Test
    @DisplayName("Kilit süresi dolunca hesap kendiliğinden açılır ve sayaç sıfırlanır")
    void sureDoluncaKilitAcilir() {
        kullanici.setFailedLoginAttempts(LoginAttemptService.MAX_FAILED_ATTEMPTS);
        kullanici.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // süresi dolmuş kilit

        assertFalse(loginAttemptService.isLocked(kullanici), "Süresi dolmuş kilit engellememeli");
        assertNull(kullanici.getLockedUntil());
        assertEquals(0, kullanici.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("Kilit süresi dolmadan hesap açılmaz")
    void sureDolmadanAcilmaz() {
        kullanici.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        assertTrue(loginAttemptService.isLocked(kullanici));
        assertTrue(loginAttemptService.remainingLockMinutes(kullanici) > 0);
    }

    @Test
    @DisplayName("Başarılı giriş sayacı ve kilidi temizler")
    void basariliGirisSayaciSifirlar() {
        kullanici.setFailedLoginAttempts(3);
        kullanici.setLockedUntil(LocalDateTime.now().plusMinutes(5));

        loginAttemptService.recordSuccess(kullanici);

        assertEquals(0, kullanici.getFailedLoginAttempts());
        assertNull(kullanici.getLockedUntil());
        verify(userRepository).save(kullanici);
    }

    @Test
    @DisplayName("Temiz hesapta başarılı giriş gereksiz kayıt yapmaz")
    void temizHesaptaGereksizKayitYok() {
        loginAttemptService.recordSuccess(kullanici);
        verify(userRepository, never()).save(any());
    }
}
