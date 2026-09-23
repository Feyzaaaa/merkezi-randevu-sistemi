package com.hastane.merkezi_randevu_sistemi.repository;
import com.hastane.merkezi_randevu_sistemi.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // Yönetici paneli: aynı isimde ikinci bir poliklinik tanımlanmasını engellemek için
    boolean existsByNameIgnoreCase(String name);
}