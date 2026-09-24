package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.dto.DoctorCreateRequest;
import com.hastane.merkezi_randevu_sistemi.dto.RoleUpdateRequest;
import com.hastane.merkezi_randevu_sistemi.model.AuditAction;
import com.hastane.merkezi_randevu_sistemi.model.Department;
import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.service.AuditService;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuditService auditService;

    public AdminController(AdminService adminService, AuditService auditService) {
        this.adminService = adminService;
        this.auditService = auditService;
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
                                            Authentication authentication,
                                            HttpServletRequest httpRequest) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        try {
            var oncekiRol = adminService.getUsers().stream()
                    .filter(u -> u.getId().equals(id)).findFirst()
                    .map(u -> u.getRole().name()).orElse("?");
            var sonuc = adminService.updateUserRole(id, request.getRole(), current.getUserId());

            // Yetki matrisini çalışma anında değiştiren işlem: iz kaydı şart
            auditService.record(current, AuditAction.ROLE_CHANGED, "User", id,
                    sonuc.getEmail() + ": " + oncekiRol + " -> " + sonuc.getRole(), httpRequest);

            return ResponseEntity.ok(sonuc);
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
    public ResponseEntity<?> createDepartment(@RequestBody Department department,
                                              Authentication authentication,
                                              HttpServletRequest httpRequest) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        try {
            Department olusan = adminService.createDepartment(department.getName());
            auditService.record(current, AuditAction.DEPARTMENT_CREATED, "Department",
                    olusan.getId(), olusan.getName(), httpRequest);
            return ResponseEntity.ok(olusan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Doktor tanımlama: mevcut kullanıcıya poliklinik + unvan atar ve rolünü DOCTOR'a yükseltir
    @PostMapping("/doctors")
    public ResponseEntity<?> createDoctor(@RequestBody DoctorCreateRequest request,
                                          Authentication authentication,
                                          HttpServletRequest httpRequest) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        try {
            Doctor olusan = adminService.createDoctor(request);
            auditService.record(current, AuditAction.DOCTOR_CREATED, "Doctor", olusan.getId(),
                    olusan.getUser().getEmail() + " doktor olarak tanımlandı ("
                            + olusan.getDepartment().getName() + ")", httpRequest);
            return ResponseEntity.ok(olusan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Denetim kayıtları: kim, ne zaman, neyi yaptı. Yalnızca okunur —
     * kayıtları silen veya değiştiren bir uç bilinçli olarak tanımlanmamıştır.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size,
                                          @RequestParam(required = false) String action) {
        if (action != null && !action.isBlank()) {
            try {
                return ResponseEntity.ok(auditService.getLogsByAction(AuditAction.valueOf(action), page, size));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Geçersiz işlem türü: " + action);
            }
        }
        return ResponseEntity.ok(auditService.getLogs(page, size));
    }
}
