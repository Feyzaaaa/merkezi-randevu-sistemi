package com.hastane.merkezi_randevu_sistemi.service;
import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus; // UNUTMA!
import com.hastane.merkezi_randevu_sistemi.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    public Appointment createAppointment(Appointment appointment) {
        // 1. NULL KONTROLÜ
        if (appointment.getDoctor() == null || appointment.getPatient() == null) {
            throw new RuntimeException("Doktor veya Hasta bilgisi eksik!");
        }

        // 2. Doktor Çakışma Kontrolü
        if (appointmentRepository.existsByDoctorIdAndAppointmentDate(
                appointment.getDoctor().getId(), appointment.getAppointmentDate())) {
            throw new RuntimeException("Bu doktorun bu saatte randevusu zaten dolu!");
        }

        // 3. Hasta Çakışma Kontrolü (Tez konun!)
        if (appointmentRepository.existsByPatientIdAndAppointmentDate(
                appointment.getPatient().getId(), appointment.getAppointmentDate())) {
            throw new RuntimeException("Aynı saatte başka bir randevunuz zaten bulunuyor!");
        }

        // --- KRİTİK DÜZELTME BURASI ---
        // Tırnak işaretlerini kaldırdık, Enum tipini kullandık.
        appointment.setStatus(AppointmentStatus.PENDING); 
        
        return appointmentRepository.save(appointment);
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }
}