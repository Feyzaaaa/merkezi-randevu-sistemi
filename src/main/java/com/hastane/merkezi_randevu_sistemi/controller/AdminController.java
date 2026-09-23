package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.dto.DoctorCreateRequest;
import com.hastane.merkezi_randevu_sistemi.dto.RoleUpdateRequest;
import com.hastane.merkezi_randevu_sistemi.model.Department;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * YÖNETİCİ PORTALI
 *
 * Bu sınıftaki tüm uç noktalar SecurityConfig'te "/api/admin/**" deseniyle ADMIN rolüne
 * kilitlenmiştir; PATIENT veya DOCTOR token'ı ile yapılan istek filtre katmanında 403 alır
 * ve buraya hiç ulaşmaz.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // Özet ekranı: rol dağılımı, randevu durumları, bugünün randevu sayısı
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    // Kullanıcı listesi (şifre hash'i taşınmaz, sadeleştirilmiş görünüm döner)
    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        return ResponseEntity.ok(adminService.getUsers());
    }

    // Rol değiştirme: yetkilendirme matrisini çalışma anında değiştiren en kritik işlem
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @RequestBody RoleUpdateRequest request,
                                            Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(adminService.updateUserRole(id, request.getRole(), current.getUserId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Denetim: sistemdeki tüm randevular (hasta ve doktor kendi listesiyle sınırlıyken yönetici hepsini görür)
    @GetMapping("/appointments")
    public ResponseEntity<?> getAllAppointments() {
        return ResponseEntity.ok(adminService.getAllAppointments());
    }

    // Poliklinik tanımlama
    @PostMapping("/departments")
    public ResponseEntity<?> createDepartment(@RequestBody Department department) {
        try {
            return ResponseEntity.ok(adminService.createDepartment(department.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Doktor tanımlama: mevcut kullanıcıya poliklinik + unvan atar ve rolünü DOCTOR'a yükseltir
    @PostMapping("/doctors")
    public ResponseEntity<?> createDoctor(@RequestBody DoctorCreateRequest request) {
        try {
            return ResponseEntity.ok(adminService.createDoctor(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
