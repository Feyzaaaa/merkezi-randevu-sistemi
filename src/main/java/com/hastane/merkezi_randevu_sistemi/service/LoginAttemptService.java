package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * GİRİŞ DENEMESİ DENETİMİ (kaba kuvvet saldırısına karşı)
 *
 * Ardışık başarısız denemeler kullanıcı kaydında sayılır ve eşik aşılınca hesap
 * geçici olarak kilitlenir. Sayaç veritabanında tutulur; sunucu yeniden başlasa
 * bile kilit devam eder (bellekte tutulsaydı saldırgan için yeniden başlatma
 * bir kaçış yolu olurdu).
 *
 * Kilit kalıcı değildir: süre dolduğunda hesap kendiliğinden açılır. Kalıcı kilit,
 * saldırganın başkasının hesabını bilerek kilitlemesine (hizmet engelleme) yol açardı.
 */
@Service
public class LoginAttemptService {

    /** Bu sayıda ardışık başarısız denemeden sonra hesap kilitlenir */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Kilidin süresi */
    public static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Hesap şu anda kilitli mi? Süresi dolmuş kilitler otomatik olarak açılır. */
    @Transactional
    public boolean isLocked(User user) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil == null) {
            return false;
        }
        if (lockedUntil.isAfter(LocalDateTime.now())) {
            return true;
        }
        // Süre dolmuş: kilidi kaldır ve sayacı sıfırla
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        return false;
    }

    /** Kilidin bitmesine kaç dakika kaldığı (kullanıcıya bildirmek için) */
    public long remainingLockMinutes(User user) {
        if (user.getLockedUntil() == null) return 0;
        long minutes = Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes();
        return Math.max(minutes + 1, 1); // 30 saniye kalsa bile "1 dakika" denir
    }

    /**
     * Başarısız denemeyi kaydeder.
     * @return eşiğe ulaşıldığı için hesap bu denemede kilitlendiyse true
     */
    @Transactional
    public boolean recordFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        boolean kilitlendi = false;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            kilitlendi = true;
        }
        userRepository.save(user);
        return kilitlendi;
    }

    /** Başarılı girişte sayaç sıfırlanır */
    @Transactional
    public void recordSuccess(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    /** Kilitlenmeden önce kaç deneme hakkı kaldığı */
    public int remainingAttempts(User user) {
        return Math.max(MAX_FAILED_ATTEMPTS - user.getFailedLoginAttempts(), 0);
    }
}
