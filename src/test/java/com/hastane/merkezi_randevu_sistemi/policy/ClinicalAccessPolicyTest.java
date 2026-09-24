package com.hastane.merkezi_randevu_sistemi.policy;

import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.repository.AppointmentRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorLeaveRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * BAĞLAM FARKINDALI ERİŞİM POLİTİKASININ TESTİ
 *
 * Üç koşulun (tedavi ilişkisi, mesai penceresi, görevde olma) tüm anlamlı
 * kombinasyonları ve acil erişim davranışı sınanır. Erişim anı dışarıdan
 * verildiği için sonuçlar testin çalıştığı saatten bağımsızdır.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClinicalAccessPolicyTest {

    private static final Long DOKTOR_KULLANICI_ID = 10L;
    private static final Long DOKTOR_ID = 5L;
    private static final Long HASTA_ID = 20L;

    /** Mesai içi bir an: Salı 10:00 */
    private static final LocalDateTime MESAI_ICI = LocalDateTime.of(2026, 5, 19, 10, 0);
    /** Mesai dışı bir an: aynı gün 21:30 */
    private static final LocalDateTime MESAI_DISI = LocalDateTime.of(2026, 5, 19, 21, 30);

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private DoctorLeaveRepository doctorLeaveRepository;

    private ClinicalAccessPolicy politika;
    private ClinicalAccessProperties ayarlar;

    @BeforeEach
    void hazirla() {
        ayarlar = new ClinicalAccessProperties();
        politika = new ClinicalAccessPolicy(appointmentRepository, doctorRepository, doctorLeaveRepository, ayarlar);

        Doctor doktor = new Doctor();
        doktor.setId(DOKTOR_ID);
        when(doctorRepository.findByUserId(DOKTOR_KULLANICI_ID)).thenReturn(Optional.of(doktor));

        // Varsayılan: ilişki var, izinli değil
        tedaviIliskisi(true);
        izinliGun(false);
    }

    private void tedaviIliskisi(boolean var) {
        when(appointmentRepository.existsTedaviIliskisi(eq(DOKTOR_KULLANICI_ID), eq(HASTA_ID), any(), any()))
                .thenReturn(var);
    }

    private void izinliGun(boolean izinli) {
        when(doctorLeaveRepository.existsByDoctorIdAndLeaveDate(eq(DOKTOR_ID), any(LocalDate.class)))
                .thenReturn(izinli);
    }

    private ClinicalAccessPolicy.Karar karar(LocalDateTime an) {
        return politika.degerlendir(DOKTOR_KULLANICI_ID, HASTA_ID, an);
    }

    // --- ÜÇ KOŞUL SAĞLANDIĞINDA ---

    @Test
    @DisplayName("Kendi hastası + mesai içinde + görevde → erişim verilir")
    void tumKosullarSaglanincaErisimVerilir() {
        var k = karar(MESAI_ICI);

        assertTrue(k.izinli());
        assertFalse(k.acilErisim());
        assertTrue(k.tedaviIliskisiVar());
        assertTrue(k.mesaiIcinde());
        assertTrue(k.gorevde());
    }

    // --- K1: TEDAVİ İLİŞKİSİ ---

    @Test
    @DisplayName("K1: Hasta bu doktora atanmamışsa erişim reddedilir — hasta gizliliği")
    void baskaninHastasiGorulemez() {
        tedaviIliskisi(false);

        var k = karar(MESAI_ICI);

        assertFalse(k.izinli());
        assertFalse(k.tedaviIliskisiVar());
        assertTrue(k.sebep().contains("size atanmamış"),
                "Red gerekçesi anlaşılır olmalı: " + k.sebep());
    }

    @Test
    @DisplayName("K1 önceliklidir: ilişki yoksa mesai içinde olmak da yetmez")
    void iliskiYoksaMesaiYetmez() {
        tedaviIliskisi(false);

        var k = karar(MESAI_ICI);

        assertFalse(k.izinli());
        assertTrue(k.mesaiIcinde(), "Mesai koşulu sağlanıyor olmasına rağmen erişim verilmemeli");
    }

    // --- K2: MESAİ PENCERESİ ---

    @Test
    @DisplayName("K2: Mesai dışında kendi hastasına bile erişilemez")
    void mesaiDisindaErisimYok() {
        var k = karar(MESAI_DISI);

        assertFalse(k.izinli());
        assertTrue(k.tedaviIliskisiVar(), "İlişki var ama zaman koşulu sağlanmıyor");
        assertFalse(k.mesaiIcinde());
        assertTrue(k.sebep().contains("Mesai saatleri dışında"), k.sebep());
    }

    @Test
    @DisplayName("K2 sınır: mesai başlangıcı dahil, bitişi hariçtir")
    void mesaiSinirlari() {
        assertTrue(karar(MESAI_ICI.withHour(8).withMinute(0)).izinli(), "08:00 mesai içi sayılmalı");
        assertTrue(karar(MESAI_ICI.withHour(17).withMinute(59)).izinli(), "17:59 mesai içi sayılmalı");
        assertFalse(karar(MESAI_ICI.withHour(18).withMinute(0)).izinli(), "18:00 mesai dışı sayılmalı");
        assertFalse(karar(MESAI_ICI.withHour(7).withMinute(59)).izinli(), "07:59 mesai dışı sayılmalı");
    }

    @Test
    @DisplayName("K2 ayarlanabilir: mesai penceresi genişletilince erişim açılır")
    void mesaiPenceresiAyarlanabilir() {
        assertFalse(karar(MESAI_DISI).izinli());

        ayarlar.setMesaiBitis(java.time.LocalTime.of(23, 0));

        assertTrue(karar(MESAI_DISI).izinli(), "Pencere genişletilince 21:30 erişime açılmalı");
    }

    // --- K3: GÖREVDE OLMA ---

    @Test
    @DisplayName("K3: İzinli gündeki doktor klinik veriye erişemez")
    void izinliGunErisimYok() {
        izinliGun(true);

        var k = karar(MESAI_ICI);

        assertFalse(k.izinli());
        assertFalse(k.gorevde());
        assertTrue(k.sebep().contains("izinli"), k.sebep());
    }

    @Test
    @DisplayName("Doktor kaydı olmayan kullanıcı klinik veriye erişemez")
    void doktorKaydiYoksaErisimYok() {
        when(doctorRepository.findByUserId(DOKTOR_KULLANICI_ID)).thenReturn(Optional.empty());

        assertFalse(karar(MESAI_ICI).izinli());
    }

    // --- ACİL ERİŞİM (kırıl-camı) ---

    @Test
    @DisplayName("Acil erişim: gerekçeyle mesai dışında da erişilebilir ve işaretlenir")
    void acilErisimMesaiDisindaCalisir() {
        var k = politika.degerlendir(DOKTOR_KULLANICI_ID, HASTA_ID, MESAI_DISI,
                "Hasta acil serviste, tahlil sonucu gerekli");

        assertTrue(k.izinli());
        assertTrue(k.acilErisim(), "Acil erişim olarak işaretlenmeliydi");
        assertFalse(k.mesaiIcinde(), "Koşulun sağlanmadığı kayda geçmeli");
        assertTrue(k.denetimOzeti().contains("ACİL ERİŞİM"));
    }

    @Test
    @DisplayName("Acil erişim, atanmamış hasta için de çalışır ama iz bırakır")
    void acilErisimAtanmamisHastaIcinDeCalisir() {
        tedaviIliskisi(false);

        var k = politika.degerlendir(DOKTOR_KULLANICI_ID, HASTA_ID, MESAI_ICI,
                "Nöbetteki hasta devri, acil değerlendirme");

        assertTrue(k.izinli());
        assertTrue(k.acilErisim());
        assertFalse(k.tedaviIliskisiVar(), "İlişkinin olmadığı denetim kaydında görünmeli");
    }

    @Test
    @DisplayName("Yetersiz gerekçeyle acil erişim kabul edilmez")
    void kisaGerekceKabulEdilmez() {
        var k = politika.degerlendir(DOKTOR_KULLANICI_ID, HASTA_ID, MESAI_DISI, "acil");

        assertFalse(k.izinli(), "Gerekçe eşiğin altında; politika aşılmamalı");
        assertFalse(k.acilErisim());
    }

    @Test
    @DisplayName("Acil erişim kapatılabilir: kapalıyken gerekçe de politikayı aşmaz")
    void acilErisimKapatilabilir() {
        ayarlar.setAcilErisimAcik(false);

        var k = politika.degerlendir(DOKTOR_KULLANICI_ID, HASTA_ID, MESAI_DISI,
                "Hasta acil serviste, tahlil sonucu gerekli");

        assertFalse(k.izinli());
        assertFalse(k.acilErisim());
    }

    @Test
    @DisplayName("Koşullar zaten sağlanıyorsa gerekçe verilse bile acil erişim sayılmaz")
    void kosullarSaglaninceAcilSayilmaz() {
        var k = politika.degerlendir(DOKTOR_KULLANICI_ID, HASTA_ID, MESAI_ICI,
                "Gereksiz yere gönderilmiş bir acil gerekçesi");

        assertTrue(k.izinli());
        assertFalse(k.acilErisim(), "Normal yoldan erişilebiliyorsa acil erişim işaretlenmemeli");
    }

    // --- DENETİM ÖZETİ ---

    @Test
    @DisplayName("Karar, denetim kaydına yazılabilecek okunabilir bir özet üretir")
    void denetimOzetiUretilir() {
        tedaviIliskisi(false);

        String ozet = karar(MESAI_DISI).denetimOzeti();

        assertTrue(ozet.contains("ilişki=yok"));
        assertTrue(ozet.contains("mesai=dışında"));
        assertTrue(ozet.contains("görevde=evet"));
    }
}
