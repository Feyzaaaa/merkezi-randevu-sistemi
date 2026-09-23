package com.hastane.merkezi_randevu_sistemi.controller;
import com.hastane.merkezi_randevu_sistemi.dto.ClinicalNoteRequest;
import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private DoctorRepository doctorRepository;

    @PostMapping
    public ResponseEntity<?> createAppointment(@RequestBody Appointment appointment, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();

        // SAHİPLİK KONTROLÜ: Hasta sadece kendi adına randevu oluşturabilir, başka bir patientId'yi taklit edemez
        if (appointment.getPatient() == null || appointment.getPatient().getId() == null
                || !appointment.getPatient().getId().equals(current.getUserId())) {
            return ResponseEntity.status(403).body("Sadece kendi adınıza randevu oluşturabilirsiniz!");
        }

        try {
            Appointment created = appointmentService.createAppointment(appointment);
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentService.getAllAppointments();
    }

    // Doktor Portalı: bir doktora ait tüm randevuları döner (sadece kendi randevu listesi)
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<?> getAppointmentsByDoctor(@PathVariable Long doctorId, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        if (!doctorRepository.existsByIdAndUserId(doctorId, current.getUserId())) {
            return ResponseEntity.status(403).body("Sadece kendi randevu listenizi görebilirsiniz!");
        }
        return ResponseEntity.ok(appointmentService.getAppointmentsByDoctor(doctorId));
    }

    // Hasta Portalı: bir hastaya ait tüm randevuları döner (sadece kendi geçmişi)
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getAppointmentsByPatient(@PathVariable Long patientId, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        if (!patientId.equals(current.getUserId())) {
            return ResponseEntity.status(403).body("Sadece kendi randevu geçmişinizi görebilirsiniz!");
        }
        return ResponseEntity.ok(appointmentService.getAppointmentsByPatient(patientId));
    }

    // Doktor Portalı: bir randevuya vaka notu / reçete bilgisi kaydeder (sadece kendi randevusuna)
    @PatchMapping("/{id}/note")
    public ResponseEntity<?> saveClinicalNote(@PathVariable Long id, @RequestBody ClinicalNoteRequest request, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        try {
            Appointment appointment = appointmentService.getById(id);
            if (appointment.getDoctor() == null || appointment.getDoctor().getUser() == null
                    || !appointment.getDoctor().getUser().getId().equals(current.getUserId())) {
                return ResponseEntity.status(403).body("Sadece kendi randevunuza not girebilirsiniz!");
            }
            Appointment updated = appointmentService.saveClinicalNote(id, request.getNote());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Doktor kendi randevusunu onaylar (PENDING -> CONFIRMED)
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<?> confirmAppointment(@PathVariable Long id, Authentication authentication) {
        return changeStatus(id, AppointmentStatus.CONFIRMED, authentication);
    }

    // Doktor muayeneyi tamamlandı olarak işaretler (PENDING/CONFIRMED -> COMPLETED)
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> completeAppointment(@PathVariable Long id, Authentication authentication) {
        return changeStatus(id, AppointmentStatus.COMPLETED, authentication);
    }

    // Durum değişikliği yalnızca randevunun sahibi doktor tarafından yapılabilir
    private ResponseEntity<?> changeStatus(Long id, AppointmentStatus target, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        try {
            Appointment appointment = appointmentService.getById(id);
            if (appointment.getDoctor() == null || appointment.getDoctor().getUser() == null
                    || !appointment.getDoctor().getUser().getId().equals(current.getUserId())) {
                return ResponseEntity.status(403).body("Sadece kendi randevunuzun durumunu değiştirebilirsiniz!");
            }
            return ResponseEntity.ok(appointmentService.updateStatus(id, target));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Hasta veya Doktor kendi randevusunu, Yönetici ise denetim amacıyla her randevuyu iptal edebilir
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        try {
            Appointment appointment = appointmentService.getById(id);
            boolean isOwnerPatient = appointment.getPatient() != null
                    && appointment.getPatient().getId().equals(current.getUserId());
            boolean isOwnerDoctor = appointment.getDoctor() != null && appointment.getDoctor().getUser() != null
                    && appointment.getDoctor().getUser().getId().equals(current.getUserId());
            // Yönetici denetim yetkisi: sahiplik aranmadan herhangi bir randevuyu iptal edebilir
            boolean isAdmin = "ADMIN".equals(current.getRole());
            if (!isOwnerPatient && !isOwnerDoctor && !isAdmin) {
                return ResponseEntity.status(403).body("Sadece kendi randevunuzu iptal edebilirsiniz!");
            }
            // R10: Son dakika iptali yalnızca hasta için engellenir; doktor ve yönetici
            // operasyonel gerekçeyle (hasta gelmedi, doktor rahatsızlandı) her an iptal edebilir.
            Appointment updated = appointmentService.cancelAppointment(id, isOwnerPatient && !isAdmin);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // YENİ: 15'er dakikalık dinamik saatleri (08:00-18:00, öğle molası hariç) Frontend'e gönderir
    @GetMapping("/available-slots")
    public ResponseEntity<List<String>> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<String> slots = appointmentService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(slots);
    }
}