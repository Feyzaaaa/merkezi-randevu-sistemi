package com.hastane.merkezi_randevu_sistemi.security;

// JWT'den çözülen kimlik bilgisi; SecurityContext'teki principal olarak taşınır,
// controller'lar bunu Authentication.getPrincipal() ile alıp SAHİPLİK kontrolü yapar
// (örn. "bu randevu/profil gerçekten bu kullanıcıya mı ait?").
public class AuthenticatedUser {

    private final Long userId;
    private final String email;
    private final String role;

    public AuthenticatedUser(Long userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
