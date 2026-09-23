package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.LabResult;
import com.hastane.merkezi_randevu_sistemi.repository.LabResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LabResultService {

    @Autowired
    private LabResultRepository labResultRepository;

    public List<LabResult> getByPatientId(Long patientId) {
        return labResultRepository.findByPatientIdOrderByTestDateDesc(patientId);
    }

    public LabResult addResult(LabResult labResult) {
        if (labResult.getPatient() == null || labResult.getPatient().getId() == null) {
            throw new RuntimeException("Hasta bilgisi eksik!");
        }
        return labResultRepository.save(labResult);
    }
}
