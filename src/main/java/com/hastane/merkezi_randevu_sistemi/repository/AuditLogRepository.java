package com.hastane.merkezi_randevu_sistemi.repository;

import com.hastane.merkezi_randevu_sistemi.model.AuditAction;
import com.hastane.merkezi_randevu_sistemi.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Denetim kayıtları yalnızca yazılır ve okunur; silme/güncelleme uçları
 * bilinçli olarak sunulmaz (bkz. AuditLog sınıfı açıklaması).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    // Giriş güvenliği: belirli bir e-posta için son X dakikadaki başarısız deneme sayısı
    long countByActorEmailAndActionAndTimestampAfter(String actorEmail, AuditAction action, LocalDateTime after);
}
