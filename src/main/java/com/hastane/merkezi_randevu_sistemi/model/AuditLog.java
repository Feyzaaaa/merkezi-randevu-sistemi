package com.hastane.merkezi_randevu_sistemi.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * DENETİM KAYDI (AUDIT LOG)
 *
 * Yetkilendirmeyi ilgilendiren her kritik işlem buraya yazılır: kim (actor),
 * ne zaman, neyi (target), hangi adresten. Kayıtlar yalnızca eklenir —
 * güncelleme veya silme uçları bilinçli olarak tanımlanmamıştır; aksi hâlde
 * izin kendisi değiştirilebilir olurdu ve denetim değerini yitirirdi.
 *
 * Başarısız giriş denemeleri de kaydedilir: kimliği doğrulanmamış bir istek
 * olduğu için actorUserId boş kalır, e-posta ve IP bilgisi tutulur.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "ix_audit_logs_timestamp", columnList = "timestamp"),
        @Index(name = "ix_audit_logs_action", columnList = "action")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    // İşlemi yapan (kimliksiz istekte boş olabilir)
    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_role")
    private String actorRole;

    // İşlemin etkilediği kayıt (kullanıcı, randevu, poliklinik...)
    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    // İnsan tarafından okunabilir açıklama: "PATIENT -> ADMIN" gibi
    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(nullable = false)
    private boolean success = true;

    public AuditLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }

    // Yanıtta okunabilir etiket de dönsün (arayüz enum adını göstermesin)
    public String getActionLabel() { return action == null ? "-" : action.getLabel(); }

    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
