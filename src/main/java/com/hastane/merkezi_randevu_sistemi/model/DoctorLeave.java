package com.hastane.merkezi_randevu_sistemi.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * DOKTOR İZİN / GÖREV GÜNÜ
 *
 * Doktorun randevuya kapalı olduğu günleri tutar. Bu kayıt varsa o gün için
 * müsait saat üretilmez ve o güne randevu oluşturulamaz (kural R7).
 */
@Entity
@Table(
    name = "doctor_leaves",
    // Aynı doktor için aynı gün iki kez izin tanımlanamaz
    uniqueConstraints = @UniqueConstraint(columnNames = {"doctor_id", "leave_date"})
)
public class DoctorLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    private String reason;

    public DoctorLeave() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }

    public LocalDate getLeaveDate() { return leaveDate; }
    public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
