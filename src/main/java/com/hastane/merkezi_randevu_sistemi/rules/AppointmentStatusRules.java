package com.hastane.merkezi_randevu_sistemi.rules;

import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * RANDEVU DURUM MAKİNESİ
 *
 * Bir randevunun yaşam döngüsü:
 *
 *     PENDING ──onayla──> CONFIRMED ──tamamla──> COMPLETED
 *        │                    │
 *        └──────iptal─────────┴──> CANCELLED
 *
 * COMPLETED ve CANCELLED son durumlardır; bunlardan çıkış yoktur.
 * Doktor onaylamayı atlayıp muayeneyi doğrudan tamamlayabilir (PENDING -> COMPLETED),
 * çünkü uygulamada onay adımı her zaman işletilmeyebilir.
 */
public final class AppointmentStatusRules {

    /** Hangi durumdan hangi durumlara geçilebilir */
    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED = Map.of(
            AppointmentStatus.PENDING,   Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED),
            AppointmentStatus.CONFIRMED, Set.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED),
            AppointmentStatus.COMPLETED, Set.of(),
            AppointmentStatus.CANCELLED, Set.of()
    );

    public static final Map<AppointmentStatus, String> LABELS = Map.of(
            AppointmentStatus.PENDING, "Onay Bekliyor",
            AppointmentStatus.CONFIRMED, "Onaylandı",
            AppointmentStatus.COMPLETED, "Tamamlandı",
            AppointmentStatus.CANCELLED, "İptal Edildi"
    );

    private AppointmentStatusRules() {}

    public static boolean isAllowed(AppointmentStatus from, AppointmentStatus to) {
        if (from == null || to == null) return false;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static String label(AppointmentStatus status) {
        return LABELS.getOrDefault(status, status == null ? "-" : status.name());
    }

    /**
     * Geçişi hem durum makinesine hem de zaman mantığına göre denetler.
     *
     * @return hata mesajı; geçiş uygunsa boş Optional
     */
    public static Optional<String> validateTransition(AppointmentStatus current,
                                                      AppointmentStatus target,
                                                      LocalDateTime appointmentDate,
                                                      LocalDateTime now) {
        if (current == target) {
            return Optional.of("Randevu zaten '" + label(current) + "' durumunda!");
        }
        if (!isAllowed(current, target)) {
            return Optional.of("'" + label(current) + "' durumundaki bir randevu '"
                    + label(target) + "' yapılamaz!");
        }
        // Gerçekleşmemiş bir muayene tamamlanmış sayılamaz
        if (target == AppointmentStatus.COMPLETED
                && appointmentDate != null && appointmentDate.isAfter(now)) {
            return Optional.of("Randevu saati henüz gelmedi; muayene tamamlandı olarak işaretlenemez!");
        }
        // Geçmişte kalmış bir randevu onaylanmaz; ya tamamlanır ya iptal edilir
        if (target == AppointmentStatus.CONFIRMED
                && appointmentDate != null && appointmentDate.isBefore(now)) {
            return Optional.of("Randevu saati geçmiş; onaylamak yerine tamamlandı olarak işaretleyin veya iptal edin!");
        }
        return Optional.empty();
    }
}
