package com.hastane.merkezi_randevu_sistemi.dto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String email;
    private String password;

    // --- MANUEL GETTERLAR (Kırmızı çizgileri söndüren asıl yer burası!) ---
    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}