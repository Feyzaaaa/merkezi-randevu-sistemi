package com.hastane.merkezi_randevu_sistemi.repository;

import com.hastane.merkezi_randevu_sistemi.model.DoctorLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {

    // Kural R7: doktor o gün izinli mi?
    boolean existsByDoctorIdAndLeaveDate(Long doctorId, LocalDate leaveDate);

    // Doktorun kendi izin günleri listesi (yakın tarih üstte)
    List<DoctorLeave> findByDoctorIdOrderByLeaveDateAsc(Long doctorId);

    // Belirli bir tarihten itibaren olan izinler (geçmiş izinleri listede göstermemek için)
    List<DoctorLeave> findByDoctorIdAndLeaveDateGreaterThanEqualOrderByLeaveDateAsc(Long doctorId, LocalDate from);
}
