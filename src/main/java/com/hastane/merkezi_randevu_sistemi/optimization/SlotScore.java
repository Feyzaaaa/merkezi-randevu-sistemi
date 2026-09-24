package com.hastane.merkezi_randevu_sistemi.optimization;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * BİR ADAYIN DEĞERLENDİRME SONUCU
 *
 * Yalnızca toplam maliyeti değil, her bileşeni ayrı ayrı taşır. Böylece arayüz
 * "bu saat neden önerildi?" sorusunu yanıtlayabilir ve tezde tek bir adayın
 * maliyet dökümü tablo olarak gösterilebilir.
 *
 * Maliyet DÜŞÜK olan aday daha iyidir.
 */
public record SlotScore(
        Long doctorId,
        String doctorAdi,
        String poliklinik,
        LocalDateTime tarih,
        long beklemeGun,

        // --- maliyet bileşenleri (ağırlıksız ham değerler) ---
        double beklemeMaliyeti,
        double dolulukMaliyeti,
        double varyansMaliyeti,
        double riskMaliyeti,

        // --- açıklayıcı ara değerler ---
        double gunDolulugu,      // bu randevu eklendikten SONRAKİ doluluk oranı
        double iptalOlasiligi,   // modelin bu hasta+saat için tahmini

        double toplamMaliyet
) {

    /**
     * Arayüzde gösterilecek tek cümlelik gerekçe.
     * @JsonProperty olmadan Jackson yalnızca record bileşenlerini serileştirir;
     * bu türetilmiş alan yanıta girmez ve arayüzde boş görünürdü.
     */
    @JsonProperty("aciklama")
    public String aciklama() {
        String dolulukIfadesi;
        if (gunDolulugu < 0.5) {
            dolulukIfadesi = "doktorun günü henüz seyrek";
        } else if (gunDolulugu <= 0.9) {
            dolulukIfadesi = "doktorun günü dengeli dolulukta";
        } else {
            dolulukIfadesi = "doktorun günü neredeyse dolu";
        }

        String beklemeIfadesi = switch ((int) Math.min(beklemeGun, 3)) {
            case 0 -> "bugün";
            case 1 -> "yarın";
            case 2, 3 -> beklemeGun + " gün sonra";
            default -> beklemeGun + " gün sonra";
        };

        return "%s · %s · iptal olasılığı %%%.0f".formatted(
                beklemeIfadesi, dolulukIfadesi, iptalOlasiligi * 100);
    }
}
