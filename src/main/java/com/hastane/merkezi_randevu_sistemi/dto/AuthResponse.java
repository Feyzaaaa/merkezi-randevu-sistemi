package com.hastane.merkezi_randevu_sistemi.dto;
import com.hastane.merkezi_randevu_sistemi.model.Role;

public class AuthResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private String token;

    // HATAYI ÇÖZECEK OLAN 6 PARAMETRELİ CONSTRUCTOR
    public AuthResponse(Long id, String email, String firstName, String lastName, Role role, String token) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.token = token;
    }

    // IDE'de Lombok annotation processing kapalıysa diye manuel köprüler (Aynı şekilde kalsın):
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Role getRole() { return role; }
    public String getToken() { return token; }
}