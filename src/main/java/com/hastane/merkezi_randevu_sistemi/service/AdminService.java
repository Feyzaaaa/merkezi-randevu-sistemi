package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.dto.AdminStatsResponse;
import com.hastane.merkezi_randevu_sistemi.dto.DoctorCreateRequest;
import com.hastane.merkezi_randevu_sistemi.dto.UserSummaryResponse;
import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * YÖNETİCİ (ADMIN) İŞ KURALLARI
 *
 * Rol tabanlı yetkilendirmenin üçüncü katmanı: PATIENT ve DOCTOR kendi verisiyle sınırlıyken,
 * ADMIN sistem genelini görür ve rol/tanım yönetimi yapar. Buradaki kontroller "rolüm ne?"
 * sorusunun ötesine geçip "bu değişiklik sistemi tutarsız bırakır mı?" sorusunu da yanıtlar
 * (örn. yönetici kendi yetkisini düşüremez, doktor kaydı olan kullanıcının rolü düşürülemez).
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final AppointmentRepository appointmentRepository;

    public AdminService(UserRepository userRepository,
                        DoctorRepository doctorRepository,
                        DepartmentRepository departmentRepository,
                        AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // --- ÖZET ---

    public AdminStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        long todayCount = appointmentRepository
                .findByAppointmentDateBetween(today.atStartOfDay(), today.atTime(LocalTime.MAX))
                .stream()
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED)
                .count();

        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByRole(Role.PATIENT),
                userRepository.countByRole(Role.DOCTOR),
                userRepository.countByRole(Role.ADMIN),
                appointmentRepository.count(),
                appointmentRepository.countByStatus(AppointmentStatus.PENDING),
                appointmentRepository.countByStatus(AppointmentStatus.CONFIRMED),
                appointmentRepository.countByStatus(AppointmentStatus.COMPLETED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELLED),
                todayCount,
                departmentRepository.count()
        );
    }

    // --- KULLANICI VE ROL YÖNETİMİ ---

    public List<UserSummaryResponse> getUsers() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(user -> new UserSummaryResponse(user, doctorRepository.existsByUserId(user.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Bir kullanıcının rolünü değiştirir. Rol tabanlı yetkilendirmenin en hassas işlemi
     * olduğu için sisteme kilitlenmeye ve yetim kayda yol açacak senaryolar burada engellenir.
     */
    @Transactional
    public UserSummaryResponse updateUserRole(Long targetUserId, String requestedRole, Long currentAdminUserId) {
        if (targetUserId.equals(currentAdminUserId)) {
            throw new IllegalArgumentException("Kendi rolünüzü değiştiremezsiniz! (Sisteme yönetici erişimini kaybetme riski)");
        }

        Role newRole = parseRole(requestedRole);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + targetUserId));

        if (target.getRole() == newRole) {
            throw new IllegalArgumentException("Kullanıcı zaten bu role sahip!");
        }

        boolean hasDoctorRecord = doctorRepository.existsByUserId(targetUserId);

        // DOCTOR rolü, doctors tablosundaki bir kayıtla (poliklinik + unvan) birlikte anlam taşır;
        // sadece rolü değiştirmek doktoru randevu sisteminde görünmez/çalışmaz bırakırdı.
        if (newRole == Role.DOCTOR && !hasDoctorRecord) {
            throw new IllegalArgumentException(
                    "Bu kullanıcının doktor kaydı yok. Önce 'Doktor Tanımla' ekranından poliklinik ve unvan atayın.");
        }
        if (target.getRole() == Role.DOCTOR && hasDoctorRecord) {
            throw new IllegalArgumentException(
                    "Bu kullanıcı bir doktor kaydına bağlı; rolü düşürülürse randevuları sahipsiz kalır.");
        }
        if (target.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("Sistemde en az bir yönetici kalmalıdır!");
        }

        target.setRole(newRole);
        User saved = userRepository.save(target);
        return new UserSummaryResponse(saved, doctorRepository.existsByUserId(saved.getId()));
    }

    private Role parseRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            throw new IllegalArgumentException("Rol bilgisi boş olamaz!");
        }
        try {
            return Role.valueOf(requestedRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Geçersiz rol: " + requestedRole + " (PATIENT, DOCTOR veya ADMIN olmalı)");
        }
    }

    // --- TANIMLAMA İŞLEMLERİ ---

    @Transactional
    public Department createDepartment(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Poliklinik adı boş olamaz!");
        }
        String cleanName = name.trim();
        if (departmentRepository.existsByNameIgnoreCase(cleanName)) {
            throw new IllegalArgumentException("Bu isimde bir poliklinik zaten var: " + cleanName);
        }
        return departmentRepository.save(new Department(cleanName));
    }

    /**
     * Mevcut bir kullanıcıyı doktor olarak tanımlar: doctors kaydını açar ve aynı işlemde
     * kullanıcının rolünü DOCTOR'a yükseltir (rol ile kayıt her zaman birlikte değişir).
     */
    @Transactional
    public Doctor createDoctor(DoctorCreateRequest request) {
        if (request.getUserId() == null || request.getDepartmentId() == null) {
            throw new IllegalArgumentException("Kullanıcı ve poliklinik seçimi zorunludur!");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + request.getUserId()));

        if (doctorRepository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("Bu kullanıcı zaten doktor olarak tanımlı!");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Poliklinik bulunamadı: " + request.getDepartmentId()));

        user.setRole(Role.DOCTOR);
        userRepository.save(user);

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setDepartment(department);
        doctor.setTitle(request.getTitle() == null || request.getTitle().isBlank() ? "Dr." : request.getTitle().trim());
        return doctorRepository.save(doctor);
    }

    // --- DENETİM ---

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAllByOrderByAppointmentDateDesc();
    }
}
