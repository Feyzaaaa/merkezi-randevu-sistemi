package com.hastane.merkezi_randevu_sistemi.model;

/** Yeniden planlama önerisinin durumu */
public enum ProposalStatus {

    /** Hastanın yanıtı bekleniyor; önerilen slot REZERVE (kural R11) */
    PENDING("Yanıtınız bekleniyor"),

    /** Hasta kabul etti; yerine yeni randevu oluşturuldu */
    ACCEPTED("Kabul edildi"),

    /** Hasta reddetti; rezervasyon serbest bırakıldı */
    REJECTED("Reddedildi"),

    /** Süresi doldu; rezervasyon kendiliğinden düştü */
    EXPIRED("Süresi doldu");

    private final String label;

    ProposalStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
