package com.hastane.merkezi_randevu_sistemi.dto;

/** Doktorun program bozulması bildirimi (izin, acil görev, hastalık) */
public class DisruptionRequest {

    private String date;      // yyyy-MM-dd
    private String reason;

    /**
     * Bozulma bütçesi: kademeli taşımada yerinden edilebilecek en fazla
     * "etkilenmemiş" randevu sayısı. 0 = katı minimal müdahale.
     */
    private int budget = 0;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getBudget() { return budget; }
    public void setBudget(int budget) { this.budget = budget; }
}
