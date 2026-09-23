package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DoctorService {

    @Autowired
    private DoctorRepository doctorRepository;

    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    public Optional<Doctor> getDoctorByUserId(Long userId) {
        return doctorRepository.findByUserId(userId);
    }

    // --- EKSİK OLAN VE HATAYA SEBEP OLAN METOT BURASI ---
    public Doctor saveDoctor(Doctor doctor) {
        return doctorRepository.save(doctor);
    }
}