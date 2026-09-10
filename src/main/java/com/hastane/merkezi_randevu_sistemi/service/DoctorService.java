package com.hastane.merkezi_randevu_sistemi.service;
import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DoctorService {

    @Autowired
    private DoctorRepository doctorRepository;

    // EKSİK OLAN METOT BURASI:
    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }
}