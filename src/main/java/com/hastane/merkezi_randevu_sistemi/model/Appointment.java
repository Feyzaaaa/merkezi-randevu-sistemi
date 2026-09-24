package com.hastane.merkezi_randevu_sistemi.model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(nullable = false)
    private LocalDateTime appointmentDate;

    @Column(columnDefinition = "TEXT")
    private String complaint;

    // Doktorun muayene sonrası girdiği vaka notu / reçete bilgisi
    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    // Randevunun ALINDIĞI an. Bekleme süresi = appointmentDate - createdAt.
    // Optimizasyon motoru hem varyans hesabında hem de iptal riski modelinin
    // "randevuya kalan gün" özniteliğinde bu alanı kullanır.
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // --- HATALARI ÇÖZEN MANUEL METOTLAR ---
    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }

    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }

    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { this.appointmentDate = appointmentDate; }

    public void setStatus(AppointmentStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Hastanın kaç gün beklediği; alınma zamanı bilinmiyorsa boş döner */
    public java.util.OptionalLong getWaitDays() {
        if (createdAt == null || appointmentDate == null) return java.util.OptionalLong.empty();
        return java.util.OptionalLong.of(java.time.Duration.between(createdAt, appointmentDate).toDays());
    }

    // --- YENİ EKLENEN NOT KÖPRÜLERİ ---
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}