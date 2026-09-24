package com.hastane.merkezi_randevu_sistemi.rules;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * ŞİFRE POLİTİKASI
 *
 * Kimlik doğrulamanın gücü, en zayıf şifre kadardır: yetkilendirme ne kadar
 * doğru kurgulanırsa kurgulansın, "123456" şifreli bir yönetici hesabı tüm
 * matrisi geçersiz kılar. Bu sınıf kayıt sırasında asgari koşulları uygular.
 *
 * Not: Kural yalnızca YENİ kayıtlarda çalışır; örnek veriyle gelen demo
 * hesapları doğrudan veritabanına yazıldığı için etkilenmez.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    // En sık kullanılan ve sözlük saldırılarında ilk denenen şifreler
    private static final Set<String> YAYGIN_SIFRELER = Set.of(
            "12345678", "123456789", "password", "parola123", "qwerty123",
            "11111111", "abcd1234", "sifre123", "admin123", "hastane1"
    );

    private PasswordPolicy() {}

    /**
     * @return kullanıcıya gösterilecek hata mesajı; şifre uygunsa boş Optional
     */
    public static Optional<String> validate(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("Şifre boş olamaz!");
        }
        if (password.length() < MIN_LENGTH) {
            return Optional.of("Şifre en az " + MIN_LENGTH + " karakter olmalıdır!");
        }

        boolean harfVar = password.chars().anyMatch(Character::isLetter);
        boolean rakamVar = password.chars().anyMatch(Character::isDigit);

        if (!harfVar || !rakamVar) {
            return Optional.of("Şifre en az bir harf ve en az bir rakam içermelidir!");
        }
        // DİKKAT: toLowerCase() yerel ayara duyarlıdır. Türkçe ayarda "ADMIN123" -> "admın123"
        // (noktasız ı) olur ve liste eşleşmez; bu da kuralın sessizce atlatılması demektir.
        // Locale.ROOT ile karşılaştırma yerel ayardan bağımsız çalışır.
        if (YAYGIN_SIFRELER.contains(password.toLowerCase(Locale.ROOT))) {
            return Optional.of("Bu şifre çok yaygın kullanılıyor, lütfen daha özgün bir şifre seçin!");
        }
        if (password.chars().distinct().count() < 4) {
            return Optional.of("Şifre yeterince çeşitli karakter içermiyor!");
        }
        return Optional.empty();
    }

    public static String requirementsText() {
        return "En az " + MIN_LENGTH + " karakter, en az bir harf ve bir rakam içermeli.";
    }
}
