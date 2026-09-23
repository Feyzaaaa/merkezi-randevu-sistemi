package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.PatientProfile;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.PatientProfileRepository;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PatientProfileService {

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private UserRepository userRepository;

    // Henüz kayıt yoksa, kaydedilmemiş boş bir şablon döner (frontend "-" gösterir)
    public PatientProfile getByUserId(Long userId) {
        return patientProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    PatientProfile empty = new PatientProfile();
                    empty.setUser(findUser(userId));
                    return empty;
                });
    }

    public PatientProfile upsert(Long userId, PatientProfile incoming) {
        PatientProfile profile = patientProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    PatientProfile fresh = new PatientProfile();
                    fresh.setUser(findUser(userId));
                    return fresh;
                });

        profile.setHeight(incoming.getHeight());
        profile.setWeight(incoming.getWeight());
        profile.setAge(incoming.getAge());
        profile.setGender(incoming.getGender());
        profile.setBloodType(incoming.getBloodType());
        profile.setAllergies(incoming.getAllergies());
        profile.setPhone(incoming.getPhone());

        return patientProfileRepository.save(profile);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));
    }
}
