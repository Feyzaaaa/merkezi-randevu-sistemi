package com.hastane.merkezi_randevu_sistemi.rules;

import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RANDEVU DURUM MAKİNESİNİN TESTİ
 *
 * Her test, durum geçiş diyagramındaki bir oka (ya da olmayan bir oka) karşılık gelir.
 */
class AppointmentStatusRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 20, 12, 0);
    private static final LocalDateTime GELECEK = NOW.plusDays(1);
    private static final LocalDateTime GECMIS = NOW.minusDays(1);

    private Optional<String> gecis(AppointmentStatus from, AppointmentStatus to, LocalDateTime tarih) {
        return AppointmentStatusRules.validateTransition(from, to, tarih, NOW);
    }

    private void gecerli(AppointmentStatus from, AppointmentStatus to, LocalDateTime tarih) {
        Optional<String> sonuc = gecis(from, to, tarih);
        assertTrue(sonuc.isEmpty(), "Geçiş kabul edilmeliydi (" + from + " -> " + to + "): " + sonuc.orElse(""));
    }

    private String gecersiz(AppointmentStatus from, AppointmentStatus to, LocalDateTime tarih) {
        Optional<String> sonuc = gecis(from, to, tarih);
        assertTrue(sonuc.isPresent(), "Geçiş reddedilmeliydi: " + from + " -> " + to);
        return sonuc.get();
    }

    @Test
    @DisplayName("Onay Bekliyor -> Onaylandı (gelecek tarihli randevu)")
    void beklemedenOnaylanabilir() {
        gecerli(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, GELECEK);
    }

    @Test
    @DisplayName("Onaylandı -> Tamamlandı (randevu saati geçmişse)")
    void onaylanandanTamamlanabilir() {
        gecerli(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED, GECMIS);
    }

    @Test
    @DisplayName("Onay adımı atlanıp doğrudan tamamlanabilir")
    void beklemedenDogrudanTamamlanabilir() {
        gecerli(AppointmentStatus.PENDING, AppointmentStatus.COMPLETED, GECMIS);
    }

    @Test
    @DisplayName("Saati gelmemiş randevu tamamlandı yapılamaz")
    void gelecekRandevuTamamlanamaz() {
        String hata = gecersiz(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED, GELECEK);
        assertTrue(hata.contains("henüz gelmedi"), "Beklenmeyen mesaj: " + hata);
    }

    @Test
    @DisplayName("Saati geçmiş randevu onaylanamaz (tamamlanmalı veya iptal edilmeli)")
    void gecmisRandevuOnaylanamaz() {
        String hata = gecersiz(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, GECMIS);
        assertTrue(hata.contains("geçmiş"), "Beklenmeyen mesaj: " + hata);
    }

    @Test
    @DisplayName("İptal edilmiş randevu yeniden canlandırılamaz")
    void iptalEdilenGeriAlinamaz() {
        gecersiz(AppointmentStatus.CANCELLED, AppointmentStatus.CONFIRMED, GELECEK);
        gecersiz(AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED, GECMIS);
        gecersiz(AppointmentStatus.CANCELLED, AppointmentStatus.PENDING, GELECEK);
    }

    @Test
    @DisplayName("Tamamlanmış randevu son durumdur")
    void tamamlanandanCikisYok() {
        gecersiz(AppointmentStatus.COMPLETED, AppointmentStatus.CONFIRMED, GECMIS);
        gecersiz(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED, GECMIS);
    }

    @Test
    @DisplayName("Aynı duruma geçiş anlamlı bir mesajla reddedilir")
    void ayniDurumaGecisReddedilir() {
        String hata = gecersiz(AppointmentStatus.CONFIRMED, AppointmentStatus.CONFIRMED, GELECEK);
        assertTrue(hata.contains("zaten"), "Beklenmeyen mesaj: " + hata);
    }

    @Test
    @DisplayName("Aktif randevular iptal edilebilir")
    void aktifRandevularIptalEdilebilir() {
        gecerli(AppointmentStatus.PENDING, AppointmentStatus.CANCELLED, GELECEK);
        gecerli(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED, GELECEK);
    }

    @Test
    @DisplayName("Her durumun kullanıcıya gösterilecek Türkçe karşılığı vardır")
    void tumDurumlarinEtiketiVardir() {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            assertFalse(AppointmentStatusRules.label(status).isBlank());
            assertNotEquals(status.name(), AppointmentStatusRules.label(status));
        }
    }
}
