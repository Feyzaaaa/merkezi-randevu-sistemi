package com.hastane.merkezi_randevu_sistemi.optimization;

import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * İPTAL RİSKİ MODELİ — Lojistik Regresyon
 *
 * P(iptal) = σ(w·x + b),  σ(z) = 1 / (1 + e^(-z))
 *
 * ÖZNİTELİKLER (hepsi 0-1 aralığına normalize edilir):
 *   x1  randevuya kalan gün / 30      — bekleme uzadıkça iptal olasılığı artar
 *   x2  hastanın geçmiş iptal oranı   — Laplace düzeltmeli (az veriyle aşırı uçlara gitmesin)
 *   x3  randevu saati (08:00→0, 18:00→1) — geç saatler literatürde daha yüksek riskli
 *   x4  hastanın randevu geçmişi uzunluğu / 10 — sistemi tanıyan hasta daha az iptal eder
 *
 * EĞİTİM: Sonucu belli olmuş randevular (COMPLETED veya CANCELLED) etiketli örnek
 * olarak kullanılır; ağırlıklar toplu gradyan inişi (batch gradient descent) ve
 * L2 düzenlileştirme ile öğrenilir.
 *
 * SOĞUK BAŞLANGIÇ: Yeterli örnek yoksa (< MIN_ORNEK) eğitim yapılmaz; literatürdeki
 * yönlere uygun, elle belirlenmiş başlangıç ağırlıkları kullanılır. Bu durum
 * {@link #isEgitildi()} ile açıkça raporlanır — model "eğitilmiş gibi" davranmaz.
 *
 * SINIRLAR: Doğrusal bir modeldir; öznitelikler arası etkileşimi yakalamaz.
 * Tez kapsamında amaç en iyi kestirimi yapmak değil, iptal riskini planlama
 * maliyetine ölçülebilir biçimde dahil etmektir.
 */
public class CancellationRiskModel {

    /** Altında eğitim yapılmayan örnek sayısı */
    public static final int MIN_ORNEK = 20;

    private static final int OZNITELIK_SAYISI = 4;
    private static final int EPOK = 400;
    private static final double OGRENME_ORANI = 0.3;
    private static final double L2 = 0.01;

    /** Laplace düzeltmesi: 1 randevuda 1 iptal yapan hasta %100 riskli sayılmasın */
    private static final double LAPLACE = 2.0;

    /** Soğuk başlangıç ağırlıkları: yön bilgisi literatürden, büyüklük ihtiyatlı */
    private static final double[] VARSAYILAN_AGIRLIK = {1.2, 2.0, 0.4, -0.8};
    private static final double VARSAYILAN_SABIT = -2.2; // taban iptal oranı ≈ %10

    private double[] agirlik = VARSAYILAN_AGIRLIK.clone();
    private double sabit = VARSAYILAN_SABIT;
    private boolean egitildi = false;
    private int ornekSayisi = 0;

    public boolean isEgitildi() { return egitildi; }
    public int getOrnekSayisi() { return ornekSayisi; }
    public double[] getAgirlik() { return agirlik.clone(); }
    public double getSabit() { return sabit; }

    // --- ÖZNİTELİK ÇIKARIMI ---

    /**
     * @param kalanGun        randevuya kaç gün var (alınma anından randevu anına)
     * @param gecmisIptal     hastanın iptal ettiği randevu sayısı
     * @param gecmisToplam    hastanın sonucu belli olmuş toplam randevu sayısı
     * @param saat            randevu saati (0-23)
     */
    public static double[] oznitelik(long kalanGun, int gecmisIptal, int gecmisToplam, int saat) {
        double x1 = Math.min(Math.max(kalanGun, 0) / 30.0, 1.0);
        double x2 = (gecmisIptal + LAPLACE) / (gecmisToplam + 2 * LAPLACE);
        double x3 = Math.min(Math.max((saat - 8) / 10.0, 0.0), 1.0);
        double x4 = Math.min(gecmisToplam / 10.0, 1.0);
        return new double[]{x1, x2, x3, x4};
    }

    /** Geçmiş bir randevudan eğitim örneği çıkarır (sonucu belli değilse boş döner) */
    private static double[] egitimOrnegi(Appointment a, int gecmisIptal, int gecmisToplam) {
        if (a.getCreatedAt() == null || a.getAppointmentDate() == null) return null;
        if (a.getStatus() != AppointmentStatus.CANCELLED && a.getStatus() != AppointmentStatus.COMPLETED) return null;

        long kalanGun = Duration.between(a.getCreatedAt(), a.getAppointmentDate()).toDays();
        return oznitelik(kalanGun, gecmisIptal, gecmisToplam, a.getAppointmentDate().getHour());
    }

    // --- TAHMİN ---

    public double tahminEt(double[] x) {
        double z = sabit;
        for (int i = 0; i < OZNITELIK_SAYISI; i++) {
            z += agirlik[i] * x[i];
        }
        return sigmoid(z);
    }

    public double tahminEt(long kalanGun, int gecmisIptal, int gecmisToplam, int saat) {
        return tahminEt(oznitelik(kalanGun, gecmisIptal, gecmisToplam, saat));
    }

    private static double sigmoid(double z) {
        if (z >= 0) return 1.0 / (1.0 + Math.exp(-z));
        double e = Math.exp(z);           // büyük negatif z'de taşmayı önler
        return e / (1.0 + e);
    }

    // --- EĞİTİM ---

    /**
     * Sonucu belli olmuş randevular üzerinde modeli eğitir.
     * Örnek sayısı eşiğin altındaysa ağırlıklar değiştirilmez (soğuk başlangıç korunur).
     *
     * @param gecmisRandevular tüm randevular (sırası önemsiz)
     * @return eğitim yapıldıysa true
     */
    public boolean egit(List<Appointment> gecmisRandevular) {
        List<double[]> X = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        // Her randevu için, O RANDEVUDAN ÖNCEKİ geçmişi kullanmak gerekirdi; basitlik
        // adına hastanın genel geçmişi kullanılır. Bu, hafif bir veri sızıntısıdır ve
        // tezde modelin sınırı olarak belirtilmiştir.
        var iptalSayaci = new java.util.HashMap<Long, Integer>();
        var toplamSayaci = new java.util.HashMap<Long, Integer>();
        for (Appointment a : gecmisRandevular) {
            if (a.getPatient() == null) continue;
            Long pid = a.getPatient().getId();
            if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.COMPLETED) {
                toplamSayaci.merge(pid, 1, Integer::sum);
                if (a.getStatus() == AppointmentStatus.CANCELLED) iptalSayaci.merge(pid, 1, Integer::sum);
            }
        }

        for (Appointment a : gecmisRandevular) {
            if (a.getPatient() == null) continue;
            Long pid = a.getPatient().getId();
            double[] x = egitimOrnegi(a,
                    iptalSayaci.getOrDefault(pid, 0),
                    toplamSayaci.getOrDefault(pid, 0));
            if (x == null) continue;
            X.add(x);
            y.add(a.getStatus() == AppointmentStatus.CANCELLED ? 1.0 : 0.0);
        }

        ornekSayisi = X.size();
        if (ornekSayisi < MIN_ORNEK) {
            egitildi = false;
            return false;
        }

        double[] w = new double[OZNITELIK_SAYISI];
        double b = 0.0;

        for (int epok = 0; epok < EPOK; epok++) {
            double[] gradW = new double[OZNITELIK_SAYISI];
            double gradB = 0.0;

            for (int i = 0; i < X.size(); i++) {
                double z = b;
                for (int j = 0; j < OZNITELIK_SAYISI; j++) z += w[j] * X.get(i)[j];
                double hata = sigmoid(z) - y.get(i);

                for (int j = 0; j < OZNITELIK_SAYISI; j++) gradW[j] += hata * X.get(i)[j];
                gradB += hata;
            }

            for (int j = 0; j < OZNITELIK_SAYISI; j++) {
                // L2: ağırlıkların aşırı büyümesini engeller (az veride ezberleme riski)
                w[j] -= OGRENME_ORANI * (gradW[j] / X.size() + L2 * w[j]);
            }
            b -= OGRENME_ORANI * (gradB / X.size());
        }

        this.agirlik = w;
        this.sabit = b;
        this.egitildi = true;
        return true;
    }

    /** Modelin durumunu insan tarafından okunabilir biçimde özetler */
    public String ozet() {
        if (!egitildi) {
            return "soğuk başlangıç (yalnızca %d örnek, eşik %d)".formatted(ornekSayisi, MIN_ORNEK);
        }
        return "eğitildi (%d örnek)".formatted(ornekSayisi);
    }

    /** Test ve yeniden üretilebilirlik için: eğitim yapılmamış varsayılan model */
    public static CancellationRiskModel varsayilan() {
        return new CancellationRiskModel();
    }

    /** Şu an geçerli zamana göre kalan gün sayısı */
    public static long kalanGun(LocalDateTime simdi, LocalDateTime randevu) {
        return Math.max(Duration.between(simdi, randevu).toDays(), 0);
    }
}
