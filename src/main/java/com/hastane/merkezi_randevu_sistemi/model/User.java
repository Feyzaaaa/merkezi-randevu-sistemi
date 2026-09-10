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

    // DEĞİŞİKLİK: String yerine Role (Enum) tipini kullanıyoruz
    @Enumerated(EnumType.STRING)
    private Role role;

    public User() {}

    // --- GETTERLAR ---
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    
    @JsonProperty("firstName")
    public String getFirstName() { return firstName; }
    
    @JsonProperty("lastName")
    public String getLastName() { return lastName; }
    
    // Artık tip uyuşmazlığı hatası vermez
    public Role getRole() { return role; }

    // --- SETTERLAR ---
    public void setId(Long id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    // Artık tip uyuşmazlığı hatası vermez
    public void setRole(Role role) { this.role = role; }
}