package com.hastane.merkezi_randevu_sistemi.config;
import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final DepartmentRepository departmentRepo;
    private final DoctorRepository doctorRepo;
    private final UserRepository userRepo;

    public DataLoader(DepartmentRepository departmentRepo, DoctorRepository doctorRepo, UserRepository userRepo) {
        this.departmentRepo = departmentRepo;
        this.doctorRepo = doctorRepo;
        this.userRepo = userRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        if (departmentRepo.count() > 0) {
            System.out.println("Veritabanı zaten dolu, yeni veri eklenmedi.");
            return;
        }

        // --- 1. POLİKLİNİKLERİ OLUŞTURMA ---
        Department goz = new Department();
        goz.setName("Göz Hastalıkları");
        goz = departmentRepo.save(goz); // Save edip dönen değeri alıyoruz (ID için)

        Department dahiliye = new Department();
        dahiliye.setName("İç Hastalıkları (Dahiliye)");
        dahiliye = departmentRepo.save(dahiliye);

        Department kardiyo = new Department();
        kardiyo.setName("Kardiyoloji");
        kardiyo = departmentRepo.save(kardiyo);

        // --- 2. DOKTORLAR İÇİN USER HESAPLARI ---
        User user1 = new User();
        user1.setFirstName("Ahmet");
        user1.setLastName("Yılmaz");
        user1.setEmail("ahmet@hastane.com");
        user1.setRole(Role.DOCTOR);
        userRepo.save(user1);

        User user2 = new User();
        user2.setFirstName("Ayşe");
        user2.setLastName("Kaya");
        user2.setEmail("ayse@hastane.com");
        user2.setRole(Role.DOCTOR);
        userRepo.save(user2);

        // --- 3. DOKTORLARI OLUŞTURMA ---
        Doctor doc1 = new Doctor();
        doc1.setUser(user1);
        doc1.setDepartment(goz); // Artık 'goz' nesnesinin bir ID'si var
        doc1.setTitle("Uzm. Dr.");
        doctorRepo.save(doc1);

        Doctor doc2 = new Doctor();
        doc2.setUser(user2);
        doc2.setDepartment(dahiliye); 
        doc2.setTitle("Prof. Dr.");
        doctorRepo.save(doc2);

        System.out.println("✅ Örnek veriler başarıyla yüklendi! Artık React'te görebilirsin.");
    }
}