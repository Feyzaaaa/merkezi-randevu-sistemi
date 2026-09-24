package com.hastane.merkezi_randevu_sistemi.dto;

// Kullanıcının kendi şifresini değiştirmek için gönderdiği istek gövdesi
public class PasswordChangeRequest {

    private String currentPassword;
    private String newPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
