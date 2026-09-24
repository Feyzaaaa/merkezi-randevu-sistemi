package com.hastane.merkezi_randevu_sistemi.optimization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DOLULUK MALİYET FONKSİYONUNUN TESTİ
 *
 * Tezin savı: %100 doluluk iyi bir hedef DEĞİLDİR. Dolu bir gün mola, gecikme,
 * acil hasta ve beklenmeyen durumlar için pay bırakmaz; tek bir gecikme zincirleme
 * olarak günün tamamını kaydırır. Bu yüzden maliyet fonksiyonu asimetriktir:
 * atıl kapasite israftır (hafif ceza), aşırı yükleme risktir (ağır ceza).
 *
 * Bu testler o davranışı sayısal olarak belgeler.
 */
class UtilizationCostTest {

    private AppointmentOptimizer optimizer;
    private OptimizationWeights agirliklar;

    @BeforeEach
    void hazirla() {
        agirliklar = new OptimizationWeights(); // varsayılanlar: hedef 0.85, aşırı yük cezası 2.0
        optimizer = new AppointmentOptimizer(null, null, null, agirliklar);
    }

    private double maliyet(double doluluk) {
        return optimizer.dolulukMaliyetiHesapla(doluluk);
    }

    @Test
    @DisplayName("Hedef dolulukta maliyet sıfırdır")
    void hedeftMaliyetSifir() {
        assertEquals(0.0, maliyet(agirliklar.getHedefDoluluk()), 1e-9);
    }

    @Test
    @DisplayName("Boş gün cezalandırılır: atıl kapasite israftır")
    void bosGunCezalandirilir() {
        assertEquals(1.0, maliyet(0.0), 1e-9, "Tamamen boş gün en yüksek atıl maliyeti almalı");
    }

    @Test
    @DisplayName("Doluluk hedefe yaklaştıkça maliyet azalır")
    void hedefeYaklastikcaAzalir() {
        assertTrue(maliyet(0.2) > maliyet(0.5));
        assertTrue(maliyet(0.5) > maliyet(0.7));
        assertTrue(maliyet(0.7) > maliyet(0.85));
    }

    @Test
    @DisplayName("TAM DOLU gün, BOŞ günden daha maliyetlidir — %100 doluluk hedef değildir")
    void tamDoluGunBosGundenKotudur() {
        double bos = maliyet(0.0);
        double tamDolu = maliyet(1.0);

        assertTrue(tamDolu > bos,
                "Tam dolu gün (%.2f), boş günden (%.2f) daha maliyetli olmalı".formatted(tamDolu, bos));
        assertEquals(2.0, tamDolu, 1e-9, "Varsayılan katsayılarla tam doluluk maliyeti 2.0 olmalı");
    }

    @Test
    @DisplayName("Ceza asimetriktir: hedefin üstündeki sapma, altındaki eşit sapmadan ağırdır")
    void cezaAsimetriktir() {
        double hedef = agirliklar.getHedefDoluluk();
        double sapma = 0.10;

        double altinda = maliyet(hedef - sapma);
        double ustunde = maliyet(hedef + sapma);

        assertTrue(ustunde > altinda,
                "Aynı miktar sapmada aşırı yükleme (%.3f), atıl kapasiteden (%.3f) ağır olmalı"
                        .formatted(ustunde, altinda));
    }

    @Test
    @DisplayName("Hedef doluluk ayarlanabilir: %95 hedefte daha dolu günler tercih edilir")
    void hedefAyarlanabilir() {
        agirliklar.setHedefDoluluk(0.95);

        assertEquals(0.0, maliyet(0.95), 1e-9);
        assertTrue(maliyet(0.85) > 0, "Hedef yükselince %85 artık ideal olmamalı");
    }

    @Test
    @DisplayName("Aşırı yük cezası ayarlanabilir: katsayı artınca dolu günler daha çok cezalanır")
    void asiriYukCezasiAyarlanabilir() {
        double varsayilanCeza = maliyet(1.0);

        agirliklar.setAsiriYukCezasi(4.0);
        double artirilmisCeza = maliyet(1.0);

        assertTrue(artirilmisCeza > varsayilanCeza);
        assertEquals(4.0, artirilmisCeza, 1e-9);
    }

    @Test
    @DisplayName("Sistem kapasiteyi kullanmaya teşvik eder: yarı dolu gün, çeyrek dolu günden iyidir")
    void kapasiteKullanimiTesvikEdilir() {
        assertTrue(maliyet(0.50) < maliyet(0.25),
                "Kapasiteyi toplamak (konsolidasyon) tercih edilmeli");
    }
}
