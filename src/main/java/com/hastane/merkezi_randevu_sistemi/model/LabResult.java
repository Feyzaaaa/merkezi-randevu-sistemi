package com.hastane.merkezi_randevu_sistemi.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

// Hastaya ait laboratuvar sonucu / tahlil / analiz kaydı
@Entity
@Table(name = "lab_results")
@Data
@NoArgsConstructor
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @Column(nullable = false)
    private String testName; // örn. "Açlık Kan Şekeri"

    @Column(nullable = false)
    private String result; // örn. "95 mg/dL"

    private String referenceRange; // örn. "70-100 mg/dL"

    @Column(nullable = false)
    private LocalDate testDate;
}
