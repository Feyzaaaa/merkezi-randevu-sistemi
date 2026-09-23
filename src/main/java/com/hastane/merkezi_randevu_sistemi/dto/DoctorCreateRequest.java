package com.hastane.merkezi_randevu_sistemi.dto;

// Yöneticinin mevcut bir kullanıcıyı doktor olarak tanımlaması için gönderdiği istek gövdesi
public class DoctorCreateRequest {

    private Long userId;
    private Long departmentId;
    private String title;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
