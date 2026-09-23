package com.hastane.merkezi_randevu_sistemi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

// Hastanın genel sağlık ve iletişim bilgileri (boy, kilo, yaş, cinsiyet, kan grubu, alerjiler, telefon)
@Entity
@Table(name = "patient_profiles")
@Data
@NoArgsConstructor
public class PatientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private Integer height; // cm
    private Double weight; // kg
    private Integer age;
    private String gender;
    private String bloodType;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    private String phone;
}
