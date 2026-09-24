package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.AuditAction;
import com.hastane.merkezi_randevu_sistemi.model.AuditLog;
import com.hastane.merkezi_randevu_sistemi.repository.AuditLogRepository;
import com.hastane.merkezi_randevu_sistemi.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * DENETİM KAYDI SERVİSİ
 *
 * Kritik işlemleri iz kaydına yazar. Kayıt tutmak asıl işlemi bozmamalıdır:
 * bu yüzden yazma hatası yutulur ve yalnızca konsola düşer — denetim kaydı
 * alınamadı diye bir hastanın randevusu iptal edilememezlik yapmamalıdır.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Kimliği doğrulanmış bir kullanıcının yaptığı işlem */
    public void record(AuthenticatedUser actor, AuditAction action, String targetType,
                       Object targetId, String details, HttpServletRequest request) {
        AuditLog log = base(action, details, request, true);
        if (actor != null) {
            log.setActorUserId(actor.getUserId());
            log.setActorEmail(actor.getEmail());
            log.setActorRole(actor.getRole());
        }
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        save(log);
    }

    /** Kimliği henüz doğrulanmamış bir istek (giriş denemesi gibi) */
    public void recordAnonymous(AuditAction action, String email, String details,
                                boolean success, HttpServletRequest request) {
        AuditLog log = base(action, details, request, success);
        log.setActorEmail(email);
        log.setTargetType("User");
        save(log);
    }

    private AuditLog base(AuditAction action, String details, HttpServletRequest request, boolean success) {
        AuditLog log = new AuditLog();
        log.setTimestamp(LocalDateTime.now());
        log.setAction(action);
        log.setDetails(details);
        log.setSuccess(success);
        log.setIpAddress(clientIp(request));
        return log;
    }

    private void save(AuditLog log) {
        try {
            auditLogRepository.save(log);
        } catch (Exception e) {
            // Denetim kaydı yazılamadıysa asıl işlemi engelleme
            System.err.println("Denetim kaydı yazılamadı: " + e.getMessage());
        }
    }

    // Ters vekil (proxy) arkasında gerçek istemci adresi X-Forwarded-For başlığında olur
    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // --- OKUMA (yalnızca yönetici) ---

    public Page<AuditLog> getLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, Math.min(size, 200)));
    }

    public Page<AuditLog> getLogsByAction(AuditAction action, int page, int size) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action, PageRequest.of(page, Math.min(size, 200)));
    }

    /** Giriş güvenliği: son "dakika" içindeki başarısız deneme sayısı */
    public long countRecentFailedLogins(String email, int minutes) {
        return auditLogRepository.countByActorEmailAndActionAndTimestampAfter(
                email, AuditAction.LOGIN_FAILED, LocalDateTime.now().minusMinutes(minutes));
    }
}
