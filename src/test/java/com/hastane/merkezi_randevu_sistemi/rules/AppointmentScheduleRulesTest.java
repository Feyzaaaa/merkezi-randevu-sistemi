package com.hastane.merkezi_randevu_sistemi.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SENARYO TABANLI RANDEVU KURALLARININ TESTİ
 *
 * Her test, tezde anlatılan bir senaryoya karşılık gelir. "Şimdi" zamanı sabit
 * verildiği için testler günün saatinden bağımsız olarak hep aynı sonucu üretir.
 */
class AppointmentScheduleRulesTest {

    // Sabit referans an: 15 Ocak 2026, saat 10:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    private Optional<AppointmentScheduleRules.Violation> validate(LocalDateTime when) {
        return AppointmentScheduleRules.validate(when, NOW);
    }

    private void assertViolation(LocalDateTime when, AppointmentScheduleRules.Violation expected) {
        Optional<AppointmentScheduleRules.Violation> result = validate(when);
        assertTrue(result.isPresent(), "Kural ihlali bekleniyordu ama randevu kabul edildi: " + when);
        assertEquals(expected, result.get(), "Beklenen kural tetiklenmedi: " + when);
    }

    private void assertAccepted(LocalDateTime when) {
        assertTrue(validate(when).isEmpty(),
                "Randevu kabul edilmeliydi ama reddedildi: " + when + " -> " + validate(when).map(Enum::name).orElse(""));
    }

    @Test
    @DisplayName("R1: Tarih hiç gönderilmezse reddedilir")
    void tarihYoksaReddedilir() {
        assertViolation(null, AppointmentScheduleRules.Violation.MISSING_DATE);
    }

    @Test
    @DisplayName("R2: Geçmiş bir güne randevu alınamaz")
    void gecmisGuneRandevuAlinamaz() {
        assertViolation(LocalDateTime.of(2020, 1, 15, 10, 0), AppointmentScheduleRules.Violation.PAST_DATETIME);
    }

    @Test
    @DisplayName("R2: Bugünün geçip gitmiş saatine randevu alınamaz")
    void bugununGecmisSaatineRandevuAlinamaz() {
        assertViolation(NOW.withHour(9).withMinute(0), AppointmentScheduleRules.Violation.PAST_DATETIME);
    }

    @Test
    @DisplayName("R2: Tam olarak şu an da geçmiş sayılır")
    void tamSuAnGecmisSayilir() {
        assertViolation(NOW, AppointmentScheduleRules.Violation.PAST_DATETIME);
    }

    @Test
    @DisplayName("R3: Üst sınırın (30 gün) ötesine randevu alınamaz")
    void cokIleriTariheRandevuAlinamaz() {
        assertViolation(NOW.plusDays(31).withHour(10).withMinute(0), AppointmentScheduleRules.Violation.TOO_FAR_AHEAD);
    }

    @Test
    @DisplayName("R3 sınır: Tam 30 gün sonrası kabul edilir")
    void otuzuncuGunKabulEdilir() {
        assertAccepted(NOW.plusDays(30).withHour(10).withMinute(0));
    }

    @ParameterizedTest(name = "R4: {0} dakikası 15''lik dilime oturmuyor")
    @CsvSource({"9,7", "10,1", "14,29", "16,44"})
    @DisplayName("R4: 15 dakikalık ızgaraya oturmayan saatler reddedilir")
    void izgaraDisiSaatlerReddedilir(int saat, int dakika) {
        assertViolation(NOW.plusDays(1).withHour(saat).withMinute(dakika),
                AppointmentScheduleRules.Violation.NOT_ON_SLOT_GRID);
    }

    @ParameterizedTest(name = "R5: saat {0}:00 mesai dışı")
    @CsvSource({"3", "7", "18", "19", "23"})
    @DisplayName("R5: Mesai saatleri (08:00-18:00) dışındaki saatler reddedilir")
    void mesaiDisiSaatlerReddedilir(int saat) {
        assertViolation(NOW.plusDays(1).withHour(saat).withMinute(0),
                AppointmentScheduleRules.Violation.OUTSIDE_WORKING_HOURS);
    }

    @ParameterizedTest(name = "R6: saat 12:{0} öğle arası")
    @CsvSource({"0", "15", "30", "45"})
    @DisplayName("R6: Öğle arasıyla kesişen dilimler reddedilir")
    void ogleArasiReddedilir(int dakika) {
        assertViolation(NOW.plusDays(1).withHour(12).withMinute(dakika),
                AppointmentScheduleRules.Violation.LUNCH_BREAK);
    }

    @Test
    @DisplayName("Sınır: 11:45 dilimi öğle arasından hemen önce biter, kabul edilir")
    void ogleArasindanOncekiSonDilimKabulEdilir() {
        assertAccepted(NOW.plusDays(1).withHour(11).withMinute(45));
    }

    @Test
    @DisplayName("Sınır: 13:00 dilimi öğle arasından hemen sonra başlar, kabul edilir")
    void ogleArasindanSonrakiIlkDilimKabulEdilir() {
        assertAccepted(NOW.plusDays(1).withHour(13).withMinute(0));
    }

    @Test
    @DisplayName("Sınır: 08:00 mesainin ilk dilimidir, kabul edilir")
    void mesaininIlkDilimiKabulEdilir() {
        assertAccepted(NOW.plusDays(1).withHour(8).withMinute(0));
    }

    @Test
    @DisplayName("Sınır: 17:45 dilimi 18:00'de biter, mesainin son dilimidir")
    void mesaininSonDilimiKabulEdilir() {
        assertAccepted(NOW.plusDays(1).withHour(17).withMinute(45));
    }

    @Test
    @DisplayName("Her kural ihlali kullanıcıya gösterilebilecek bir mesaj ve kod taşır")
    void herIhlalinMesajiVeKoduVardir() {
        for (AppointmentScheduleRules.Violation violation : AppointmentScheduleRules.Violation.values()) {
            assertNotNull(violation.getCode());
            assertFalse(violation.getMessage().isBlank());
            assertTrue(violation.toUserMessage().contains(violation.getCode()));
        }
    }
}
