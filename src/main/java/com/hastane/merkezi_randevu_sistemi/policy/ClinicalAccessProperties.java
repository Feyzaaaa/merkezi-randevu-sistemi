package com.hastane.merkezi_randevu_sistemi.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * BAĞLAM FARKINDALI KLİNİK ERİŞİM PARAMETRELERİ
 *
 * Politikanın eşikleri koda gömülmez: farklı kurumlar farklı mesai pencereleri
 * ve farklı tedavi ilişkisi süreleri kullanabilir. Tez kapsamında bu değerlerin
 * değiştirilmesi politikanın davranışını nasıl etkilediğini göstermeye yarar.
 */
@Component
@ConfigurationProperties(prefix = "mhrs.klinik-erisim")
public class ClinicalAccessProperties {

    /** Klinik veriye erişimin açık olduğu mesai penceresinin başlangıcı */
    private LocalTime mesaiBaslangic = LocalTime.of(8, 0);

    /** Mesai penceresinin bitişi */
    private LocalTime mesaiBitis = LocalTime.of(18, 0);

    /**
     * Tedavi ilişkisinin GEÇMİŞE dönük geçerlilik süresi (gün).
     * Üç yıl önce bir kez muayene eden doktorun süresiz erişimi olmamalıdır.
     */
    private int iliskiGecmisGun = 90;

    /**
     * Tedavi ilişkisinin GELECEĞE dönük geçerlilik süresi (gün).
     * Yaklaşan randevusu olan doktor, muayene öncesi hazırlık için erişebilmelidir.
     */
    private int iliskiGelecekGun = 30;

    /**
     * Acil erişim ("kırıl-camı") açık mı?
     * Katı bir politika, gerçek bir acil durumda hastaya zarar verebilir. Bu yüzden
     * gerekçe bildirmek koşuluyla politikanın aşılmasına izin verilir; ancak her
     * aşım denetim kaydına belirgin biçimde yazılır. Erişim engellenmez, HESAP SORULUR.
     */
    private boolean acilErisimAcik = true;

    /** Acil erişim gerekçesinin en az kaç karakter olması gerektiği */
    private int acilGerekceMinUzunluk = 10;

    public LocalTime getMesaiBaslangic() { return mesaiBaslangic; }
    public void setMesaiBaslangic(LocalTime v) { this.mesaiBaslangic = v; }

    public LocalTime getMesaiBitis() { return mesaiBitis; }
    public void setMesaiBitis(LocalTime v) { this.mesaiBitis = v; }

    public int getIliskiGecmisGun() { return iliskiGecmisGun; }
    public void setIliskiGecmisGun(int v) { this.iliskiGecmisGun = v; }

    public int getIliskiGelecekGun() { return iliskiGelecekGun; }
    public void setIliskiGelecekGun(int v) { this.iliskiGelecekGun = v; }

    public boolean isAcilErisimAcik() { return acilErisimAcik; }
    public void setAcilErisimAcik(boolean v) { this.acilErisimAcik = v; }

    public int getAcilGerekceMinUzunluk() { return acilGerekceMinUzunluk; }
    public void setAcilGerekceMinUzunluk(int v) { this.acilGerekceMinUzunluk = v; }
}
