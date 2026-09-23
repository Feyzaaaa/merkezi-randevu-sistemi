package com.hastane.merkezi_randevu_sistemi.dto;

// Yönetici panelinin özet ekranı: sistemin rol ve randevu durumu dağılımı
public class AdminStatsResponse {

    private long totalUsers;
    private long patientCount;
    private long doctorCount;
    private long adminCount;

    private long totalAppointments;
    private long pendingCount;
    private long confirmedCount;
    private long completedCount;
    private long cancelledCount;
    private long todayCount;

    private long departmentCount;

    public AdminStatsResponse(long totalUsers, long patientCount, long doctorCount, long adminCount,
                              long totalAppointments, long pendingCount, long confirmedCount,
                              long completedCount, long cancelledCount, long todayCount,
                              long departmentCount) {
        this.totalUsers = totalUsers;
        this.patientCount = patientCount;
        this.doctorCount = doctorCount;
        this.adminCount = adminCount;
        this.totalAppointments = totalAppointments;
        this.pendingCount = pendingCount;
        this.confirmedCount = confirmedCount;
        this.completedCount = completedCount;
        this.cancelledCount = cancelledCount;
        this.todayCount = todayCount;
        this.departmentCount = departmentCount;
    }

    public long getTotalUsers() { return totalUsers; }
    public long getPatientCount() { return patientCount; }
    public long getDoctorCount() { return doctorCount; }
    public long getAdminCount() { return adminCount; }
    public long getTotalAppointments() { return totalAppointments; }
    public long getPendingCount() { return pendingCount; }
    public long getConfirmedCount() { return confirmedCount; }
    public long getCompletedCount() { return completedCount; }
    public long getCancelledCount() { return cancelledCount; }
    public long getTodayCount() { return todayCount; }
    public long getDepartmentCount() { return departmentCount; }
}
