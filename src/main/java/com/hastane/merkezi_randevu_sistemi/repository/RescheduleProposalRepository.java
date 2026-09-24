package com.hastane.merkezi_randevu_sistemi.repository;

import com.hastane.merkezi_randevu_sistemi.model.ProposalStatus;
import com.hastane.merkezi_randevu_sistemi.model.RescheduleProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RescheduleProposalRepository extends JpaRepository<RescheduleProposal, Long> {

    /** Hastanın yanıt bekleyen önerileri */
    List<RescheduleProposal> findByPatientIdAndStatusOrderByCreatedAtDesc(Long patientId, ProposalStatus status);

    List<RescheduleProposal> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    /**
     * KURAL R11 — REZERVE SLOT
     * Belirli bir doktor/tarih aralığında, hâlâ rezervasyon tutan önerileri döndürür.
     * Bu slotlar başka hastaya sunulamaz ve üzerine randevu oluşturulamaz.
     */
    @Query("""
            SELECT p FROM RescheduleProposal p
            WHERE p.doctor.id = :doctorId
              AND p.status = com.hastane.merkezi_randevu_sistemi.model.ProposalStatus.PENDING
              AND (p.expiresAt IS NULL OR p.expiresAt > :simdi)
              AND p.proposedDate >= :baslangic
              AND p.proposedDate <= :bitis
            """)
    List<RescheduleProposal> findAktifRezervasyonlar(@Param("doctorId") Long doctorId,
                                                     @Param("baslangic") LocalDateTime baslangic,
                                                     @Param("bitis") LocalDateTime bitis,
                                                     @Param("simdi") LocalDateTime simdi);

    /** Tek bir slot için rezervasyon var mı? (randevu oluşturma sırasında kontrol) */
    @Query("""
            SELECT COUNT(p) > 0 FROM RescheduleProposal p
            WHERE p.doctor.id = :doctorId
              AND p.proposedDate = :slot
              AND p.status = com.hastane.merkezi_randevu_sistemi.model.ProposalStatus.PENDING
              AND (p.expiresAt IS NULL OR p.expiresAt > :simdi)
              AND (:haricPatientId IS NULL OR p.patient.id <> :haricPatientId)
            """)
    boolean slotRezerveMi(@Param("doctorId") Long doctorId,
                          @Param("slot") LocalDateTime slot,
                          @Param("simdi") LocalDateTime simdi,
                          @Param("haricPatientId") Long haricPatientId);
}
