package com.hastane.merkezi_randevu_sistemi.optimization;

import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;
import com.hastane.merkezi_randevu_sistemi.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * İPTAL RİSKİ MODELİNİN TESTİ
 *
 * Modelin kestirim doğruluğu değil, DAVRANIŞI sınanır: hangi yönde tepki verdiği,
 * sınır değerlerde taşıp taşmadığı ve yetersiz veride dürüstçe "eğitilmedim"
 * diyip diyemediği.
 */
class CancellationRiskModelTest {

    private static final LocalDateTime SIMDI = LocalDateTime.of(2026, 5, 20, 10, 0);

    @Test
    @DisplayName("Olasılık her zaman 0-1 aralığındadır")
    void olasilikSinirlariIcinde() {
        CancellationRiskModel model = CancellationRiskModel.varsayilan();

        for (long gun : new long[]{0, 1, 15, 30, 365}) {
            for (int iptal : new int[]{0, 5, 50}) {
                for (int toplam : new int[]{0, 5, 50}) {
                    if (iptal > toplam) continue;
                    for (int saat : new int[]{0, 8, 13, 18, 23}) {
                        double p = model.tahminEt(gun, iptal, toplam, saat);
                        assertTrue(p >= 0.0 && p <= 1.0, "Olasılık aralık dışı: " + p);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Bekleme süresi uzadıkça iptal olasılığı artar")
    void uzunBeklemeRiskiArtirir() {
        CancellationRiskModel model = CancellationRiskModel.varsayilan();

        double yakin = model.tahminEt(1, 0, 5, 10);
        double orta = model.tahminEt(15, 0, 5, 10);
        double uzak = model.tahminEt(30, 0, 5, 10);

        assertTrue(yakin < orta, "15 gün, 1 günden riskli olmalı");
        assertTrue(orta < uzak, "30 gün, 15 günden riskli olmalı");
    }

    @Test
    @DisplayName("Geçmişte çok iptal eden hasta daha riskli değerlendirilir")
    void gecmisIptalRiskiArtirir() {
        CancellationRiskModel model = CancellationRiskModel.varsayilan();

        double temizGecmis = model.tahminEt(10, 0, 10, 10);
        double iptalciGecmis = model.tahminEt(10, 8, 10, 10);

        assertTrue(iptalciGecmis > temizGecmis,
                "10 randevudan 8'ini iptal eden hasta daha riskli olmalı");
    }

    @Test
    @DisplayName("Laplace düzeltmesi: tek randevuda tek iptal, %100 risk anlamına gelmez")
    void laplaceDuzeltmesiAsiriUcuEngeller() {
        CancellationRiskModel model = CancellationRiskModel.varsayilan();

        double p = model.tahminEt(10, 1, 1, 10);
        assertTrue(p < 0.9, "Tek örnekten kesin hüküm çıkarılmamalı, bulunan: " + p);
    }

    @Test
    @DisplayName("Yetersiz veride eğitim yapılmaz ve bu durum dürüstçe raporlanır")
    void yetersizVerideSogukBaslangic() {
        CancellationRiskModel model = new CancellationRiskModel();

        boolean egitildi = model.egit(ornekUret(5, 2));

        assertFalse(egitildi);
        assertFalse(model.isEgitildi());
        assertTrue(model.ozet().contains("soğuk başlangıç"));
        assertArrayEquals(CancellationRiskModel.varsayilan().getAgirlik(), model.getAgirlik(), 1e-9,
                "Eğitim yapılmadıysa ağırlıklar değişmemeli");
    }

    @Test
    @DisplayName("Yeterli veri varsa model eğitilir ve ağırlıklar güncellenir")
    void yeterliVerideEgitilir() {
        CancellationRiskModel model = new CancellationRiskModel();

        boolean egitildi = model.egit(ornekUret(60, 20));

        assertTrue(egitildi);
        assertTrue(model.isEgitildi());
        assertEquals(60, model.getOrnekSayisi());
        assertTrue(model.ozet().contains("eğitildi"));

        // Eğitilmiş model de geçerli olasılık üretmeli
        double p = model.tahminEt(10, 1, 5, 10);
        assertTrue(p >= 0 && p <= 1);
    }

    @Test
    @DisplayName("Eğitim, verideki ilişkiyi öğrenir: uzun beklemeli randevular iptal edilmişse risk artar")
    void egitimVeridekiIliskiyiOgrenir() {
        List<Appointment> veri = new ArrayList<>();

        // Uzak tarihli randevular iptal edilmiş, yakın tarihliler tamamlanmış
        for (int i = 0; i < 40; i++) {
            veri.add(randevu(i + 1L, 28, AppointmentStatus.CANCELLED));
            veri.add(randevu(1000L + i, 2, AppointmentStatus.COMPLETED));
        }

        CancellationRiskModel model = new CancellationRiskModel();
        assertTrue(model.egit(veri));

        double yakinRisk = model.tahminEt(2, 0, 1, 10);
        double uzakRisk = model.tahminEt(28, 0, 1, 10);

        assertTrue(uzakRisk > yakinRisk,
                "Model veriden 'uzun bekleme → iptal' ilişkisini öğrenmeliydi (yakın=%.3f, uzak=%.3f)"
                        .formatted(yakinRisk, uzakRisk));
    }

    // --- yardımcılar ---

    private List<Appointment> ornekUret(int adet, int iptalSayisi) {
        List<Appointment> liste = new ArrayList<>();
        for (int i = 0; i < adet; i++) {
            liste.add(randevu(i + 1L, 5 + (i % 20),
                    i < iptalSayisi ? AppointmentStatus.CANCELLED : AppointmentStatus.COMPLETED));
        }
        return liste;
    }

    private Appointment randevu(long hastaId, int beklemeGun, AppointmentStatus durum) {
        User hasta = new User();
        hasta.setId(hastaId);

        Appointment a = new Appointment();
        a.setPatient(hasta);
        a.setCreatedAt(SIMDI);
        a.setAppointmentDate(SIMDI.plusDays(beklemeGun));
        a.setStatus(durum);
        return a;
    }
}
