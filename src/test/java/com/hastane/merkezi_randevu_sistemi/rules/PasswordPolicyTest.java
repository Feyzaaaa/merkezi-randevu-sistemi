package com.hastane.merkezi_randevu_sistemi.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ŞİFRE POLİTİKASI TESTİ
 *
 * Kimlik doğrulama katmanının gücü en zayıf şifre kadardır; bu testler
 * kabul edilen ve reddedilen şifre biçimlerini belgeler.
 */
class PasswordPolicyTest {

    @ParameterizedTest(name = "kabul: {0}")
    @ValueSource(strings = {"Hastane2026", "feyza1234", "Tez2026Proje", "a1b2c3d4"})
    @DisplayName("Politikaya uyan şifreler kabul edilir")
    void uygunSifrelerKabulEdilir(String sifre) {
        assertTrue(PasswordPolicy.validate(sifre).isEmpty(), "Kabul edilmeliydi: " + sifre);
    }

    @Test
    @DisplayName("Boş şifre reddedilir")
    void bosSifreReddedilir() {
        assertTrue(PasswordPolicy.validate(null).isPresent());
        assertTrue(PasswordPolicy.validate("   ").isPresent());
    }

    @ParameterizedTest(name = "kısa: {0}")
    @ValueSource(strings = {"a1", "abc123", "feyza1"})
    @DisplayName("Asgari uzunluğun altındaki şifreler reddedilir")
    void kisaSifrelerReddedilir(String sifre) {
        assertTrue(PasswordPolicy.validate(sifre).orElse("").contains("en az " + PasswordPolicy.MIN_LENGTH));
    }

    @Test
    @DisplayName("Yalnızca rakamdan oluşan şifre reddedilir")
    void sadeceRakamReddedilir() {
        assertTrue(PasswordPolicy.validate("12345678").isPresent());
    }

    @Test
    @DisplayName("Yalnızca harften oluşan şifre reddedilir")
    void sadeceHarfReddedilir() {
        String hata = PasswordPolicy.validate("hastanesifre").orElse("");
        assertTrue(hata.contains("rakam"), "Beklenmeyen mesaj: " + hata);
    }

    @Test
    @DisplayName("Yaygın kullanılan şifreler reddedilir")
    void yayginSifrelerReddedilir() {
        String hata = PasswordPolicy.validate("admin123").orElse("");
        assertTrue(hata.contains("yaygın"), "Beklenmeyen mesaj: " + hata);
        assertTrue(PasswordPolicy.validate("ADMIN123").isPresent(), "Büyük harfle yazılması kuralı atlatmamalı");
    }

    @Test
    @DisplayName("Aynı karakterin tekrarından oluşan şifre reddedilir")
    void tekrarliSifreReddedilir() {
        assertTrue(PasswordPolicy.validate("aaaa1111").isPresent());
    }

    @Test
    @DisplayName("Gereksinim metni kullanıcıya gösterilebilir durumda")
    void gereksinimMetniVardir() {
        assertFalse(PasswordPolicy.requirementsText().isBlank());
        assertTrue(PasswordPolicy.requirementsText().contains(String.valueOf(PasswordPolicy.MIN_LENGTH)));
    }
}
