package com.hastane.merkezi_randevu_sistemi.dto;

// Doktorun izin günü tanımlarken gönderdiği istek gövdesi
public class LeaveRequest {

    private String leaveDate; // yyyy-MM-dd
    private String reason;

    public String getLeaveDate() { return leaveDate; }
    public void setLeaveDate(String leaveDate) { this.leaveDate = leaveDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
