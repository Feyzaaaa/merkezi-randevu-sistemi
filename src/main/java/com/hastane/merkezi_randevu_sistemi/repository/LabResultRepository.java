package com.hastane.merkezi_randevu_sistemi.repository;

import com.hastane.merkezi_randevu_sistemi.model.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, Long> {
    List<LabResult> findByPatientIdOrderByTestDateDesc(Long patientId);
}
