package com.hastane.merkezi_randevu_sistemi.disruption;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BOZULMA ÇÖZÜM PLANI
 *
 * Uygulanmadan önce hazırlanır ve kullanıcıya gösterilir: "23 randevunuz var,
 * 19'u için alternatif bulundu, 4'ü için yok". Böylece doktor sonucu görmeden
 * karar vermek zorunda kalmaz.
 *
 * Plan aynı zamanda ölçüm taşır: tezde politika karşılaştırması bu sayılarla yapılır.
 */
public record DisruptionPlan(
        Long doctorId,
        String doktorAdi,
        java.time.LocalDate bozulanGun,
        String gerekce,
        int butce,

        List<Kalem> kalemler,
        Olcum olcum
) {

    /** Plandaki tek bir randevunun akıbeti */
    public record Kalem(
            Long appointmentId,
            Long patientId,
            String hastaAdi,
            LocalDateTime eskiTarih,
            LocalDateTime yeniTarih,       // null ise alternatif bulunamadı
            Long yeniDoktorId,
            String yeniDoktorAdi,
            long kaymaDakika,
            boolean kademeli,              // birincil bozulmadan mı, yayılmadan mı
            String durum                   // "TAŞINACAK" | "ALTERNATİF YOK"
    ) {
        public boolean cozuldu() { return yeniTarih != null; }
    }

    /**
     * PLANIN ÖLÇÜMÜ — tezin "Bulgular" bölümünün sayıları
     *
     * @param birincilEtkilenen  bozulan gündeki randevu sayısı
     * @param kademeliEtkilenen  bütçe kullanılarak ek olarak rahatsız edilen hasta sayısı
     * @param cozulen            alternatif slot bulunan randevu sayısı
     * @param cozulemeyen        alternatif bulunamayan (iptal kalacak) randevu sayısı
     * @param ortalamaKaymaDakika çözülenlerin ortalama zaman kayması
     * @param maksimumKaymaDakika en çok kaydırılan randevunun kayması
     * @param kararlilikOrani    sistemdeki randevuların yüzde kaçına HİÇ dokunulmadığı
     */
    public record Olcum(
            int birincilEtkilenen,
            int kademeliEtkilenen,
            int cozulen,
            int cozulemeyen,
            double ortalamaKaymaDakika,
            long maksimumKaymaDakika,
            double kararlilikOrani,
            int toplamAktifRandevu
    ) {
        public int toplamDokunulan() { return birincilEtkilenen + kademeliEtkilenen; }

        public String ozet() {
            return """
                    Birincil etkilenen : %d
                    Kademeli etkilenen : %d  (bütçe kullanımı)
                    Çözülen            : %d
                    Alternatifsiz      : %d
                    Ortalama kayma     : %.1f saat
                    Maksimum kayma     : %.1f saat
                    Kararlılık oranı   : %%%.1f  (%d randevudan %d'sine dokunulmadı)"""
                    .formatted(birincilEtkilenen, kademeliEtkilenen, cozulen, cozulemeyen,
                            ortalamaKaymaDakika / 60.0, maksimumKaymaDakika / 60.0,
                            kararlilikOrani * 100, toplamAktifRandevu,
                            toplamAktifRandevu - toplamDokunulan());
        }
    }
}
