package com.hastane.merkezi_randevu_sistemi.repository;
import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    // Giriş yapan User (role=DOCTOR) için ilişkili Doctor kaydını bulmak üzere
    Optional<Doctor> findByUserId(Long userId);

    // SAHİPLİK KONTROLÜ: Bu Doctor kaydı (doctorId) gerçekten bu User'a (userId) mi ait?
    boolean existsByIdAndUserId(Long id, Long userId);

    // Yönetici paneli: bu kullanıcı zaten doktor olarak tanımlanmış mı?
    boolean existsByUserId(Long userId);
}
