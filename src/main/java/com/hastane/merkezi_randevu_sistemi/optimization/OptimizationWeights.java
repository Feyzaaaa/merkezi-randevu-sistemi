package com.hastane.merkezi_randevu_sistemi.optimization;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OPTİMİZASYON PARAMETRELERİ
 *
 * Tüm katsayılar application.properties üzerinden değiştirilebilir; böylece
 * tez kapsamında duyarlılık analizi yapılabilir (ör. hedef doluluk %85 yerine
 * %95 olsaydı öneriler nasıl değişirdi?).
 *
 * Varsayılanlar:
 *   mhrs.optimizasyon.bekleme-agirligi = 0.40
 *   mhrs.optimizasyon.doluluk-agirligi = 0.25
 *   mhrs.optimizasyon.varyans-agirligi = 0.15
 *   mhrs.optimizasyon.risk-agirligi    = 0.20
 *   mhrs.optimizasyon.hedef-doluluk    = 0.85
 *   mhrs.optimizasyon.asiri-yuk-cezasi = 2.0
 */
@Component
@ConfigurationProperties(prefix = "mhrs.optimizasyon")
public class OptimizationWeights {

    /** Hastanın bekleyeceği gün sayısının ağırlığı */
    private double beklemeAgirligi = 0.40;

    /** Doktor doluluğunun hedeften sapmasının ağırlığı */
    private double dolulukAgirligi = 0.25;

    /** Bekleme süresi varyansının (adaletin) ağırlığı */
    private double varyansAgirligi = 0.15;

    /** İptal riskinin planlama maliyetindeki ağırlığı */
    private double riskAgirligi = 0.20;

    /**
     * HEDEF DOLULUK — %100 değil!
     * Doktorun gününün tamamını doldurmak kırılgan bir plan üretir: mola, gecikme,
     * acil hasta ve beklenmeyen durumlar için pay kalmaz ve bir gecikme zincirleme
     * olarak tüm günü kaydırır. Hedef, kapasiteyi kullanırken bu payı korumaktır.
     */
    private double hedefDoluluk = 0.85;

    /**
     * Hedefin ÜSTÜNE çıkmanın cezası, altında kalmaya göre kaç kat ağır olsun.
     * Asimetriktir: atıl kapasite israftır ama aşırı yükleme risktir.
     */
    private double asiriYukCezasi = 2.0;

    /** Öneri listesinde kaç aday döndürülsün */
    private int oneriSayisi = 3;

    public double getBeklemeAgirligi() { return beklemeAgirligi; }
    public void setBeklemeAgirligi(double v) { this.beklemeAgirligi = v; }

    public double getDolulukAgirligi() { return dolulukAgirligi; }
    public void setDolulukAgirligi(double v) { this.dolulukAgirligi = v; }

    public double getVaryansAgirligi() { return varyansAgirligi; }
    public void setVaryansAgirligi(double v) { this.varyansAgirligi = v; }

    public double getRiskAgirligi() { return riskAgirligi; }
    public void setRiskAgirligi(double v) { this.riskAgirligi = v; }

    public double getHedefDoluluk() { return hedefDoluluk; }
    public void setHedefDoluluk(double v) { this.hedefDoluluk = v; }

    public double getAsiriYukCezasi() { return asiriYukCezasi; }
    public void setAsiriYukCezasi(double v) { this.asiriYukCezasi = v; }

    public int getOneriSayisi() { return oneriSayisi; }
    public void setOneriSayisi(int v) { this.oneriSayisi = v; }
}
