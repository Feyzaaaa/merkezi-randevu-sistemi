package com.hastane.merkezi_randevu_sistemi.rules;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * SENARYO TABANLI RANDEVU KURALLARI
 *
 * Randevu planlamasının "ne zaman alınabilir?" kuralları bu sınıfta toplanmıştır.
 * Aynı kurallar iki ayrı yerde kullanılır:
 *   1) getAvailableSlots  -> kullanıcıya hangi saatlerin gösterileceğini belirler (arayüz katmanı),
 *   2) createAppointment  -> kaydetmeden önce isteği doğrular (asıl güvence).
 *
 * Bu ayrım önemlidir: arayüzün uygun olmayan saatleri listelememesi bir kolaylıktır,
 * kural değildir. İstek doğrudan API'ye gönderildiğinde (tarayıcı arayüzü devre dışı
 * bırakılarak) tek koruma sunucudaki bu doğrulamadır.
 */
public final class AppointmentScheduleRules {

    // --- Planlama parametreleri ---
    public static final LocalTime WORK_START = LocalTime.of(8, 0);
    public static final LocalTime WORK_END = LocalTime.of(18, 0);
    public static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    public static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    public static final int SLOT_MINUTES = 15;
    public static final int MAX_ADVANCE_DAYS = 30;
    /** Bir hastanın aynı anda sahip olabileceği en fazla aktif (gelecek tarihli, iptal edilmemiş) randevu sayısı */
    public static final int MAX_ACTIVE_APPOINTMENTS = 5;
    /** Randevuya bu süreden az kalmışsa hasta kendi randevusunu iptal edemez (doktor ve yönetici edebilir) */
    public static final int CANCELLATION_NOTICE_HOURS = 1;

    private AppointmentScheduleRules() {}

    /**
     * Kural ihlali senaryoları. Her ihlalin bir kodu (tezde tablo olarak referans vermek için)
     * ve kullanıcıya doğrudan gösterilebilecek bir mesajı vardır.
     */
    public enum Violation {
        MISSING_DATE("R1", "Randevu tarihi belirtilmedi!"),
        PAST_DATETIME("R2", "Geçmiş bir tarih/saate randevu alınamaz!"),
        TOO_FAR_AHEAD("R3", "En fazla " + MAX_ADVANCE_DAYS + " gün sonrasına randevu alınabilir!"),
        NOT_ON_SLOT_GRID("R4", "Randevular " + SLOT_MINUTES + " dakikalık dilimlerle başlar (örn. 09:00, 09:15, 09:30)!"),
        OUTSIDE_WORKING_HOURS("R5", "Randevular yalnızca " + WORK_START + " - " + WORK_END + " saatleri arasında alınabilir!"),
        LUNCH_BREAK("R6", "Öğle arasına (" + LUNCH_START + " - " + LUNCH_END + ") randevu alınamaz!"),

        // --- Veriye bağlı senaryolar ---
        // Aşağıdaki kurallar yalnızca tarih/saate bakarak karar verilemez; veritabanındaki
        // mevcut kayıtlara ihtiyaç duyarlar ve bu yüzden AppointmentService içinde uygulanır.
        // Mesajları burada tutulur ki tüm kural kataloğu tek bir yerden okunabilsin.
        DOCTOR_ON_LEAVE("R7", "Seçtiğiniz doktor bu tarihte izinli! Lütfen başka bir gün veya doktor seçin."),
        SAME_DAY_SAME_DEPARTMENT("R8", "Aynı gün aynı poliklinikten ikinci bir randevu alamazsınız!"),
        ACTIVE_LIMIT_REACHED("R9", "En fazla " + MAX_ACTIVE_APPOINTMENTS + " aktif randevunuz olabilir. Yeni randevu için mevcut randevularınızdan birini iptal edin."),
        CANCELLATION_TOO_LATE("R10", "Randevunuza " + CANCELLATION_NOTICE_HOURS + " saatten az kaldığı için iptal edemezsiniz. Lütfen hastaneyi arayın."),

        /**
         * Program bozulması sonucu başka bir hastaya rezerve edilmiş slot.
         * Bozulmayı çözerken yeni bir çakışma üretmemek için gereklidir.
         */
        SLOT_RESERVED("R11", "Bu saat, programı değişen başka bir hasta için geçici olarak ayrılmış. Lütfen farklı bir saat seçin.");

        private final String code;
        private final String message;

        Violation(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }

        // Hata mesajı kodla birlikte döner ki hangi senaryonun tetiklendiği loglardan izlenebilsin
        public String toUserMessage() { return message + " [" + code + "]"; }
    }

    /**
     * Verilen randevu zamanını tüm planlama kurallarına karşı sınar.
     * @return ihlal edilen ilk kural; kural ihlali yoksa boş Optional
     */
    public static Optional<Violation> validate(LocalDateTime appointmentDate, LocalDateTime now) {
        if (appointmentDate == null) {
            return Optional.of(Violation.MISSING_DATE);
        }
        // R2: Geçmişe randevu alınamaz (aynı günün geçmiş saatleri de dahil)
        if (!appointmentDate.isAfter(now)) {
            return Optional.of(Violation.PAST_DATETIME);
        }
        // R3: Takvimin sonsuza açılmaması için üst sınır
        if (appointmentDate.toLocalDate().isAfter(now.toLocalDate().plusDays(MAX_ADVANCE_DAYS))) {
            return Optional.of(Violation.TOO_FAR_AHEAD);
        }

        LocalTime time = appointmentDate.toLocalTime();

        // R4: Saat, 15 dakikalık slot ızgarasına oturmalı (09:07 gibi ara değerler kabul edilmez)
        if (time.getMinute() % SLOT_MINUTES != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            return Optional.of(Violation.NOT_ON_SLOT_GRID);
        }
        // R5: Mesai penceresi — son randevu 17:45'te başlayıp 18:00'de biter
        if (time.isBefore(WORK_START) || time.plusMinutes(SLOT_MINUTES).isAfter(WORK_END)) {
            return Optional.of(Violation.OUTSIDE_WORKING_HOURS);
        }
        // R6: Öğle arasıyla kesişen dilimler kapalıdır
        if (time.isBefore(LUNCH_END) && time.plusMinutes(SLOT_MINUTES).isAfter(LUNCH_START)) {
            return Optional.of(Violation.LUNCH_BREAK);
        }
        return Optional.empty();
    }

    /** Bu zaman dilimi hastaya gösterilebilir mi? (slot listesini süzmek için) */
    public static boolean isSelectable(LocalDateTime appointmentDate, LocalDateTime now) {
        return validate(appointmentDate, now).isEmpty();
    }
}
