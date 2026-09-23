package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"}, allowedHeaders = "*", allowCredentials = "true")
@RequestMapping("/api/doctors")
public class DoctorController {

    @Autowired
    private DoctorService doctorService;

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

    // --- DÜZELTİLEN YER (Parantez eklendi) ---
    @PostMapping
    public Doctor createDoctor(@RequestBody Doctor doctor) {
        return doctorService.saveDoctor(doctor);
    }
}