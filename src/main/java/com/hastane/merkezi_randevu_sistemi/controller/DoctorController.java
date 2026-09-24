package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.disruption.DisruptionPlan;
import com.hastane.merkezi_randevu_sistemi.disruption.ScheduleDisruptionService;
import com.hastane.merkezi_randevu_sistemi.dto.DisruptionRequest;
import com.hastane.merkezi_randevu_sistemi.dto.LeaveRequest;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;
import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.repository.AppointmentRepository;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"}, allowedHeaders = "*", allowCredentials = "true")
@RequestMapping("/api/doctors")
public class DoctorController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ScheduleDisruptionService disruptionService;

    // Bu doktor kaydı giriş yapan kullanıcıya mı ait? (izin günleri kendi kaydı için yönetilir)
    private boolean isOwnDoctorRecord(Long doctorId, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return doctorService.getDoctorByUserId(current.getUserId())
                .map(doctor -> doctor.getId().equals(doctorId))
                .orElse(false);
    }

    @GetMapping
    public List<Doctor> getAllDoctors() {
        return doctorService.getAllDoctors();
    }

    // Giriş yapan User (role=DOCTOR) kendi Doctor kaydını (ve dolayısıyla doctorId'sini) bulmak için kullanır
    // SAHİPLİK KONTROLÜ: sadece kendi userId'niz için sorgu yapabilirsiniz
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<?> getDoctorByUserId(@PathVariable Long userId, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        if (!userId.equals(current.getUserId())) {
            return ResponseEntity.status(403).body("Sadece kendi doktor kaydınızı sorgulayabilirsiniz!");
        }
        return doctorService.getDoctorByUserId(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // NOT: Doktor tanımlama ucu bilinçli olarak burada DEĞİL, AdminController'dadır
    // (POST /api/admin/doctors). Buradaki eski uç girdi doğrulaması yapmıyor ve
    // denetim kaydı bırakmıyordu; aynı işin denetimsiz ikinci bir yolu olmasın diye
    // kaldırıldı.

    // --- DOKTOR İZİN / GÖREV GÜNLERİ (kural R7) ---
    // Doktor yalnızca kendi izin günlerini görüntüler ve yönetir.

    @GetMapping("/{doctorId}/leaves")
    public ResponseEntity<?> getLeaves(@PathVariable Long doctorId, Authentication authentication) {
        if (!isOwnDoctorRecord(doctorId, authentication)) {
            return ResponseEntity.status(403).body("Yalnızca kendi izin günlerinizi görebilirsiniz!");
        }
        return ResponseEntity.ok(doctorService.getUpcomingLeaves(doctorId));
    }

    @PostMapping("/{doctorId}/leaves")
    public ResponseEntity<?> addLeave(@PathVariable Long doctorId,
                                      @RequestBody LeaveRequest request,
                                      Authentication authentication) {
        if (!isOwnDoctorRecord(doctorId, authentication)) {
            return ResponseEntity.status(403).body("Yalnızca kendi izin günlerinizi tanımlayabilirsiniz!");
        }
        try {
            LocalDate date = LocalDate.parse(request.getLeaveDate());
            // O gün iptal edilmemiş randevusu varsa izin tanımlanmasına izin verilmez
            boolean hasAppointments = appointmentRepository
                    .findByDoctorIdAndAppointmentDateBetween(doctorId, date.atStartOfDay(), date.atTime(LocalTime.MAX))
                    .stream()
                    .anyMatch(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED);

            return ResponseEntity.ok(doctorService.addLeave(doctorId, date, request.getReason(), hasAppointments));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Geçersiz tarih biçimi! (yyyy-AA-gg bekleniyor)");
        }
    }

    @DeleteMapping("/{doctorId}/leaves/{leaveId}")
    public ResponseEntity<?> removeLeave(@PathVariable Long doctorId,
                                         @PathVariable Long leaveId,
                                         Authentication authentication) {
        if (!isOwnDoctorRecord(doctorId, authentication)) {
            return ResponseEntity.status(403).body("Yalnızca kendi izin günlerinizi kaldırabilirsiniz!");
        }
        try {
            doctorService.removeLeave(doctorId, leaveId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- PROGRAM BOZULMASI (Schedule Disruption) ---
    // İzin ucu, o günde randevu varsa işlemi reddeder ve sorumluluğu kullanıcıya
    // bırakır. Bu uçlar bozulmayı sistemin çözmesini sağlar: mevcut plan mümkün
    // olduğunca korunarak etkilenen randevulara alternatif üretilir.

    /** Sonucu HESAPLAR, uygulamaz: doktor kararını görerek verir */
    @PostMapping("/{doctorId}/disruptions/preview")
    public ResponseEntity<?> bozulmaOnizle(@PathVariable Long doctorId,
                                           @RequestBody DisruptionRequest request,
                                           Authentication authentication) {
        if (!isOwnDoctorRecord(doctorId, authentication)) {
            return ResponseEntity.status(403).body("Yalnızca kendi programınız için işlem yapabilirsiniz!");
        }
        try {
            return ResponseEntity.ok(disruptionService.onizle(
                    doctorId, LocalDate.parse(request.getDate()), gerekce(request), request.getBudget()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Geçersiz tarih biçimi! (yyyy-AA-gg bekleniyor)");
        }
    }

    /** Planı uygular ve o gün için izin kaydı oluşturur */
    @PostMapping("/{doctorId}/disruptions/apply")
    public ResponseEntity<?> bozulmaUygula(@PathVariable Long doctorId,
                                           @RequestBody DisruptionRequest request,
                                           Authentication authentication) {
        if (!isOwnDoctorRecord(doctorId, authentication)) {
            return ResponseEntity.status(403).body("Yalnızca kendi programınız için işlem yapabilirsiniz!");
        }
        try {
            LocalDate gun = LocalDate.parse(request.getDate());
            DisruptionPlan plan = disruptionService.onizle(doctorId, gun, gerekce(request), request.getBudget());
            disruptionService.uygula(plan);

            // Randevular çözüldüğüne göre o gün artık izinli olarak işaretlenebilir
            doctorService.addLeave(doctorId, gun, gerekce(request), false);
            return ResponseEntity.ok(plan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Geçersiz tarih biçimi! (yyyy-AA-gg bekleniyor)");
        }
    }

    private static String gerekce(DisruptionRequest request) {
        return request.getReason() == null || request.getReason().isBlank()
                ? "Doktorun programında değişiklik" : request.getReason().trim();
    }
}
