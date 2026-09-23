package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.model.LabResult;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.LabResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lab-results")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class LabResultController {

    @Autowired
    private LabResultService labResultService;

    // Hasta Portalı: hastanın tüm laboratuvar sonuçlarını listeler (sadece kendi sonuçları)
    // Doktor rolü ise herhangi bir hastanın sonuçlarını görebilir (klinik ihtiyaç)
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getByPatient(@PathVariable Long patientId, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        boolean isSelf = patientId.equals(current.getUserId());
        boolean isDoctor = "DOCTOR".equals(current.getRole());
        if (!isSelf && !isDoctor) {
            return ResponseEntity.status(403).body("Sadece kendi laboratuvar sonuçlarınızı görebilirsiniz!");
        }
        return ResponseEntity.ok(labResultService.getByPatientId(patientId));
    }

    // Doktor Portalı: hastaya yeni bir laboratuvar sonucu ekler
    @PostMapping
    public ResponseEntity<?> addResult(@RequestBody LabResult labResult) {
        try {
            return ResponseEntity.ok(labResultService.addResult(labResult));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
