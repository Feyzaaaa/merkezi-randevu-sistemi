package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"}) // Frontend erişimi için hayati önemde!
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    // 1. YENİ RANDEVU OLUŞTURMA (POST İsteği)
    @PostMapping
    public ResponseEntity<?> createAppointment(@RequestBody Appointment appointment) {
        try {
            // Servis katmanındaki çakışma kontrollerinden geçerse veritabanına kaydeder
            Appointment created = appointmentService.createAppointment(appointment);
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            // Çakışma hatası ("Bu saat dolu!" vb.) gelirse React'e 400 hatası ve mesajı gönderir
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. TÜM RANDEVULARI LİSTELEME (GET İsteği)
    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentService.getAllAppointments();
    }
}