package com.hastane.merkezi_randevu_sistemi.repository;
import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> { }