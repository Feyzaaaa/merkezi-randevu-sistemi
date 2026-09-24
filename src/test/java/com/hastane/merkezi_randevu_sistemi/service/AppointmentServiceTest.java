package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.AppointmentRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorLeaveRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import com.hastane.merkezi_randevu_sistemi.repository.RescheduleProposalRepository;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.rules.AppointmentScheduleRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VERİYE BAĞLI ÇAKIŞMA SENARYOLARININ TESTİ (R7-R10)
 *
 * Bu kurallar yalnızca tarih/saate bakarak değil, veritabanındaki mevcut kayıtlara
 * bakarak karar verir. Depolar taklit edilerek her senaryo tek tek sınanır.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private DoctorLeaveRepository doctorLeaveRepository;
    // R11: rezerve slot kontrolü için servise eklenen bağımlılık
    @Mock private RescheduleProposalRepository rescheduleProposalRepository;
    @Mock private EmailService emailService;

    @InjectMocks private AppointmentService appointmentService;

    private static final Long HASTA_ID = 100L;
    private static final Long DOKTOR_ID = 200L;
    private static final Long POLIKLINIK_ID = 300L;

    private LocalDateTime gecerliZaman;
    private Doctor doktor;

    @BeforeEach
    void hazirla() {
        // Yarın saat 09:00: tüm zaman kurallarına (R2-R6) uyan geçerli bir dilim
        gecerliZaman = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);

        Department poliklinik = new Department("Dahiliye");
        poliklinik.setId(POLIKLINIK_ID);

        User doktorKullanici = new User();
        doktorKullanici.setId(999L);
        doktorKullanici.setEmail("doktor@test.com");
        doktorKullanici.setFirstName("Ahmet");
        doktorKullanici.setLastName("Yılmaz");

        doktor = new Doctor();
        doktor.setId(DOKTOR_ID);
        doktor.setDepartment(poliklinik);
        doktor.setUser(doktorKullanici);
        doktor.setTitle("Uzm. Dr.");

        // Varsayılan: hiçbir kısıt yok, randevu alınabilir
        when(doctorRepository.findById(DOKTOR_ID)).thenReturn(Optional.of(doktor));
        when(doctorLeaveRepository.existsByDoctorIdAndLeaveDate(eq(DOKTOR_ID), any())).thenReturn(false);
        when(appointmentRepository.existsSameDayAppointmentInDepartment(anyLong(), anyLong(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.countActiveAppointments(anyLong(), any())).thenReturn(0L);
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndStatusNot(anyLong(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.existsByPatientIdAndAppointmentDateAndStatusNot(anyLong(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        // Varsayılan: hiçbir slot rezerve değil
        when(rescheduleProposalRepository.slotRezerveMi(anyLong(), any(), any(), any())).thenReturn(false);
        when(rescheduleProposalRepository.findAktifRezervasyonlar(anyLong(), any(), any(), any()))
                .thenReturn(java.util.List.of());
        when(userRepository.findById(HASTA_ID)).thenReturn(Optional.of(hasta()));
    }

    private User hasta() {
        User user = new User();
        user.setId(HASTA_ID);
        user.setEmail("hasta@test.com");
        user.setFirstName("Ayşe");
        user.setLastName("Demir");
        return user;
    }

    private Appointment randevuTalebi(LocalDateTime zaman) {
        User patient = new User();
        patient.setId(HASTA_ID);

        Doctor doctorRef = new Doctor();
        doctorRef.setId(DOKTOR_ID);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctorRef);
        appointment.setAppointmentDate(zaman);
        return appointment;
    }

    private String olusturVeHatayiAl(LocalDateTime zaman) {
        return assertThrows(RuntimeException.class,
                () -> appointmentService.createAppointment(randevuTalebi(zaman))).getMessage();
    }

    @Test
    @DisplayName("Kurallara uyan randevu kaydedilir ve PENDING durumuyla başlar")
    void gecerliRandevuKaydedilir() {
        Appointment sonuc = appointmentService.createAppointment(randevuTalebi(gecerliZaman));

        assertEquals(AppointmentStatus.PENDING, sonuc.getStatus());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("R7: Doktor o gün izinliyse randevu oluşturulamaz")
    void izinliDoktoraRandevuAlinamaz() {
        when(doctorLeaveRepository.existsByDoctorIdAndLeaveDate(DOKTOR_ID, gecerliZaman.toLocalDate()))
                .thenReturn(true);

        assertTrue(olusturVeHatayiAl(gecerliZaman).contains("izinli"));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("R7: İzinli günde hiç müsait saat sunulmaz")
    void izinliGundeSaatSunulmaz() {
        when(doctorLeaveRepository.existsByDoctorIdAndLeaveDate(eq(DOKTOR_ID), any())).thenReturn(true);

        assertTrue(appointmentService.getAvailableSlots(DOKTOR_ID, gecerliZaman.toLocalDate()).isEmpty());
    }

    @Test
    @DisplayName("R8: Aynı gün aynı poliklinikten ikinci randevu alınamaz")
    void ayniGunAyniPoliklinikReddedilir() {
        when(appointmentRepository.existsSameDayAppointmentInDepartment(
                eq(HASTA_ID), eq(POLIKLINIK_ID), any(), any())).thenReturn(true);

        assertTrue(olusturVeHatayiAl(gecerliZaman).contains("aynı poliklinikten"));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("R9: Aktif randevu sınırına ulaşan hasta yeni randevu alamaz")
    void aktifRandevuSiniriAsilamaz() {
        when(appointmentRepository.countActiveAppointments(eq(HASTA_ID), any()))
                .thenReturn((long) AppointmentScheduleRules.MAX_ACTIVE_APPOINTMENTS);

        assertTrue(olusturVeHatayiAl(gecerliZaman).contains("aktif randevunuz"));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("R9 sınır: Sınırın bir altındaki hasta randevu alabilir")
    void sinirinAltindaRandevuAlinabilir() {
        when(appointmentRepository.countActiveAppointments(eq(HASTA_ID), any()))
                .thenReturn((long) AppointmentScheduleRules.MAX_ACTIVE_APPOINTMENTS - 1);

        assertDoesNotThrow(() -> appointmentService.createAppointment(randevuTalebi(gecerliZaman)));
    }

    @Test
    @DisplayName("Doktorun o saatte başka randevusu varsa reddedilir")
    void doktorCakismasiReddedilir() {
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndStatusNot(
                eq(DOKTOR_ID), any(), eq(AppointmentStatus.CANCELLED))).thenReturn(true);

        assertTrue(olusturVeHatayiAl(gecerliZaman).contains("doktorun bu saatte"));
    }

    @Test
    @DisplayName("Hastanın o saatte başka randevusu varsa reddedilir (farklı doktor olsa bile)")
    void hastaCakismasiReddedilir() {
        when(appointmentRepository.existsByPatientIdAndAppointmentDateAndStatusNot(
                eq(HASTA_ID), any(), eq(AppointmentStatus.CANCELLED))).thenReturn(true);

        assertTrue(olusturVeHatayiAl(gecerliZaman).contains("Aynı saatte"));
    }

    // --- R10: İPTAL PENCERESİ ---

    private Appointment mevcutRandevu(LocalDateTime zaman) {
        Appointment appointment = new Appointment();
        appointment.setPatient(hasta());
        appointment.setDoctor(doktor);
        appointment.setAppointmentDate(zaman);
        appointment.setStatus(AppointmentStatus.PENDING);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        return appointment;
    }

    @Test
    @DisplayName("R10: Randevuya 30 dakika kala hasta iptal edemez")
    void hastaSonDakikaIptalEdemez() {
        mevcutRandevu(LocalDateTime.now().plusMinutes(30));

        String hata = assertThrows(IllegalArgumentException.class,
                () -> appointmentService.cancelAppointment(1L, true)).getMessage();

        assertTrue(hata.contains("iptal edemezsiniz"), "Beklenmeyen mesaj: " + hata);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("R10: Doktor ve yönetici son dakikada da iptal edebilir")
    void doktorSonDakikaIptalEdebilir() {
        mevcutRandevu(LocalDateTime.now().plusMinutes(30));

        Appointment sonuc = appointmentService.cancelAppointment(1L, false);

        assertEquals(AppointmentStatus.CANCELLED, sonuc.getStatus());
    }

    @Test
    @DisplayName("R10: Süre sınırının dışındaki randevuyu hasta iptal edebilir")
    void hastaZamanindaIptalEdebilir() {
        mevcutRandevu(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0));

        Appointment sonuc = appointmentService.cancelAppointment(1L, true);

        assertEquals(AppointmentStatus.CANCELLED, sonuc.getStatus());
    }

    @Test
    @DisplayName("Müsait saatler öğle arasını ve dolu dilimleri içermez")
    void musaitSaatlerKurallaraUyar() {
        var saatler = appointmentService.getAvailableSlots(DOKTOR_ID, LocalDateTime.now().plusDays(1).toLocalDate());

        assertFalse(saatler.isEmpty());
        assertTrue(saatler.stream().noneMatch(s -> s.startsWith("12:")), "Öğle arası listelenmemeli");
        assertEquals("08:00 - 08:15", saatler.get(0));
        assertTrue(saatler.contains("11:45 - 12:00"), "Öğle öncesi son dilim sunulmalı");
        assertTrue(saatler.contains("13:00 - 13:15"), "Öğle sonrası ilk dilim sunulmalı");
        assertEquals("17:45 - 18:00", saatler.get(saatler.size() - 1));
        assertTrue(saatler.stream().allMatch(s -> LocalTime.parse(s.split(" - ")[0]).getMinute() % 15 == 0));
    }
}
