package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.model.PatientProfile;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.PatientProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patient-profiles")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class PatientProfileController {

    @Autowired
    private PatientProfileService patientProfileService;

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<?> getByUserId(@PathVariable Long userId, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        if (!userId.equals(current.getUserId())) {
            return ResponseEntity.status(403).body("Sadece kendi sağlık profilinizi görebilirsiniz!");
        }
        try {
            return ResponseEntity.ok(patientProfileService.getByUserId(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/by-user/{userId}")
    public ResponseEntity<?> upsert(@PathVariable Long userId, @RequestBody PatientProfile profile, Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        if (!userId.equals(current.getUserId())) {
            return ResponseEntity.status(403).body("Sadece kendi sağlık profilinizi güncelleyebilirsiniz!");
        }
        try {
            return ResponseEntity.ok(patientProfileService.upsert(userId, profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
