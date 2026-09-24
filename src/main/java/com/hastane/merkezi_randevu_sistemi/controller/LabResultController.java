package com.hastane.merkezi_randevu_sistemi.controller;

import com.hastane.merkezi_randevu_sistemi.model.AuditAction;
import com.hastane.merkezi_randevu_sistemi.model.LabResult;
import com.hastane.merkezi_randevu_sistemi.policy.ClinicalAccessPolicy;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import com.hastane.merkezi_randevu_sistemi.service.AuditService;
import com.hastane.merkezi_randevu_sistemi.service.LabResultService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * LABORATUVAR SONUÇLARI — bağlam farkındalı erişim
 *
 * Sağlık verisi, rol denetiminin tek başına yetmediği bir alandır: "DOCTOR rolü
 * tahlil okuyabilir" kuralı, hastanedeki HER doktora HER hastanın verisini açar.
 * Bu yüzden doktor erişimi {@link ClinicalAccessPolicy} ile bağlama göre daraltılır:
 * tedavi ilişkisi + mesai penceresi + görevde olma.
 *
 * Hastanın kendi verisine erişimi bağlamdan bağımsızdır; veri zaten kendisine aittir.
 */
@RestController
@RequestMapping("/api/lab-results")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class LabResultController {

    /** Acil erişim gerekçesinin taşındığı istek başlığı */
    private static final String ACIL_BASLIK = "X-Acil-Erisim";

    @Autowired
    private LabResultService labResultService;

    @Autowired
    private ClinicalAccessPolicy erisimPolitikasi;

    @Autowired
    private AuditService auditService;

    // Hasta kendi sonuçlarını görür; doktor yalnızca kendi hastasının sonuçlarını,
    // yalnızca mesai içinde ve görevdeyken görebilir.
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getByPatient(@PathVariable Long patientId,
                                          Authentication authentication,
                                          HttpServletRequest request,
                                          @RequestHeader(value = ACIL_BASLIK, required = false) String acilGerekce) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();

        // Hasta kendi verisine erişiyorsa bağlam koşulu aranmaz
        if (patientId.equals(current.getUserId())) {
            return ResponseEntity.ok(labResultService.getByPatientId(patientId));
        }

        if (!"DOCTOR".equals(current.getRole())) {
            return ResponseEntity.status(403).body("Sadece kendi laboratuvar sonuçlarınızı görebilirsiniz!");
        }

        var karar = erisimPolitikasi.degerlendir(
                current.getUserId(), patientId, LocalDateTime.now(), acilGerekce);
        denetimeYaz(current, patientId, karar, "Tahlil sonuçları görüntülendi", request);

        if (!karar.izinli()) {
            return ResponseEntity.status(403).body(karar.sebep());
        }
        return ResponseEntity.ok(labResultService.getByPatientId(patientId));
    }

    // Doktor, yalnızca tedavisini üstlendiği hastaya sonuç ekleyebilir.
    @PostMapping
    public ResponseEntity<?> addResult(@RequestBody LabResult labResult,
                                       Authentication authentication,
                                       HttpServletRequest request,
                                       @RequestHeader(value = ACIL_BASLIK, required = false) String acilGerekce) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();

        if (labResult.getPatient() == null || labResult.getPatient().getId() == null) {
            return ResponseEntity.badRequest().body("Hasta bilgisi eksik!");
        }
        Long patientId = labResult.getPatient().getId();

        var karar = erisimPolitikasi.degerlendir(
                current.getUserId(), patientId, LocalDateTime.now(), acilGerekce);
        denetimeYaz(current, patientId, karar, "Tahlil sonucu eklendi", request);

        if (!karar.izinli()) {
            return ResponseEntity.status(403).body(karar.sebep());
        }

        try {
            return ResponseEntity.ok(labResultService.addResult(labResult));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Klinik veri erişimi denetim kaydına yazılır — hem verilen hem reddedilen.
     * Sağlık verisinde "kim neye eriştiğini" sonradan gösterebilmek, erişimi
     * kısıtlamak kadar önemlidir.
     */
    private void denetimeYaz(AuthenticatedUser aktor, Long patientId,
                             ClinicalAccessPolicy.Karar karar, String islem, HttpServletRequest request) {
        AuditAction tur = karar.acilErisim()
                ? AuditAction.CLINICAL_ACCESS_EMERGENCY
                : (karar.izinli() ? AuditAction.CLINICAL_ACCESS_GRANTED : AuditAction.CLINICAL_ACCESS_DENIED);

        auditService.record(aktor, tur, "Patient", patientId,
                islem + " · " + karar.denetimOzeti() + " · " + karar.sebep(), request);
    }
}
