package com.hastane.merkezi_randevu_sistemi.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    private Role role = Role.PATIENT; // Varsayılan olarak hasta atanır

    public User() {}

    // --- GETTERLAR ---
    public Long getId() { return id; }
    public String getEmail() { return email; }

    // Şifre sadece istek gövdesinden okunur (register/login), hiçbir yanıtta JSON'a yazılmaz
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getPassword() { return password; }
    
    @JsonProperty("firstName")
    public String getFirstName() { return firstName; }
    
    @JsonProperty("lastName")
    public String getLastName() { return lastName; }
    
    public Role getRole() { return role; }

    // --- SETTERLAR ---
    public void setId(Long id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    // Gelen String rolü güvenli bir şekilde Enum'a çeviren akıllı setter
    public void setRole(Object role) {
        if (role instanceof Role) {
            this.role = (Role) role;
        } else if (role instanceof String) {
            try {
                this.role = Role.valueOf(((String) role).toUpperCase());
            } catch (IllegalArgumentException e) {
                this.role = Role.PATIENT; // Geçersizse varsayılan hasta
            }
        }
    }
}