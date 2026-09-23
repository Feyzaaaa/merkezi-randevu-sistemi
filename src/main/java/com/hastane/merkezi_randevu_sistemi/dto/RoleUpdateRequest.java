package com.hastane.merkezi_randevu_sistemi.dto;

// Yöneticinin bir kullanıcının rolünü değiştirmek için gönderdiği istek gövdesi
public class RoleUpdateRequest {

    private String role;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
