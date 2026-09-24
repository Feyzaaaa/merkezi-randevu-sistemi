package com.hastane.merkezi_randevu_sistemi.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * YENİDEN PLANLAMA ÖNERİSİ
 *
 * Doktorun programı bozulduğunda (izin, acil görev, hastalık) o güne ait randevular
 * ayakta kalamaz: doktor orada olmayacaktır. Sistem eski randevuyu iptal eder ve
 * hastaya BİR ALTERNATİF SLOT REZERVE EDER.
 *
 * Randevu doğrudan taşınmaz; hastanın onayı beklenir. Bu, hasta özerkliğini korur:
 * yeni saat işine gelmiyorsa reddedip kendisi seçebilir.
 *
 * REZERVASYON: Öneri BEKLEMEDE olduğu sürece önerilen slot başka hastaya
 * verilemez (kural R11). Aksi hâlde iki hasta aynı saati alabilir ve bozulmayı
 * çözerken yeni bir çakışma üretmiş oluruz. Rezervasyon süresi dolduğunda ya da
 * öneri reddedildiğinde slot serbest kalır.
 */
@Entity
@Table(name = "reschedule_proposals", indexes = {
        @Index(name = "ix_proposal_patient", columnList = "patient_id"),
        @Index(name = "ix_proposal_slot", columnList = "doctor_id, proposed_date")
})
public class RescheduleProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bozulmadan etkilenen, iptal edilmiş randevu */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "original_appointment_id")
    private Appointment originalAppointment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    /** Önerilen yeni doktor (aynı doktor olabilir, başka bir gün) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /** Rezerve edilen yeni slot */
    @Column(name = "proposed_date", nullable = false)
    private LocalDateTime proposedDate;

    /** Eski randevunun zamanı — kaymanın ölçülebilmesi için saklanır */
    @Column(name = "original_date")
    private LocalDateTime originalDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status = ProposalStatus.PENDING;

    /** Bozulmanın gerekçesi (hastaya gösterilir) */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /**
     * Bu öneri birincil bozulmadan mı doğdu, yoksa kademeli taşımadan mı?
     * Deney ölçümlerinde "bozulmanın yayılımı" bu alanla sayılır.
     */
    @Column(name = "cascaded", nullable = false)
    private boolean cascaded = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Bu andan sonra rezervasyon düşer ve slot serbest kalır */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public RescheduleProposal() {}

    /** Rezervasyon hâlâ geçerli mi? Yalnızca geçerli öneriler slot bloklar. */
    public boolean rezervasyonAktifMi(LocalDateTime simdi) {
        return status == ProposalStatus.PENDING
                && (expiresAt == null || expiresAt.isAfter(simdi));
    }

    /** Önerilen saatin eski saatten kaç dakika uzakta olduğu */
    public long kaymaDakika() {
        if (originalDate == null || proposedDate == null) return 0;
        return Math.abs(java.time.Duration.between(originalDate, proposedDate).toMinutes());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Appointment getOriginalAppointment() { return originalAppointment; }
    public void setOriginalAppointment(Appointment a) { this.originalAppointment = a; }

    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }

    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }

    public LocalDateTime getProposedDate() { return proposedDate; }
    public void setProposedDate(LocalDateTime d) { this.proposedDate = d; }

    public LocalDateTime getOriginalDate() { return originalDate; }
    public void setOriginalDate(LocalDateTime d) { this.originalDate = d; }

    public ProposalStatus getStatus() { return status; }
    public void setStatus(ProposalStatus s) { this.status = s; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isCascaded() { return cascaded; }
    public void setCascaded(boolean cascaded) { this.cascaded = cascaded; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime c) { this.createdAt = c; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime e) { this.expiresAt = e; }
}
