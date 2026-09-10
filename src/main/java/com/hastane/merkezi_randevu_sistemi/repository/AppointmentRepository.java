package com.hastane.merkezi_randevu_sistemi.repository;
import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Bir hastanın geçmiş randevularını listelemek için
    List<Appointment> findByPatientId(Long patientId);
    
    // Bir doktorun o günkü programını görmek için
    List<Appointment> findByDoctorId(Long doctorId);

    // ÇAKIŞMA KONTROLÜ: Bu doktorun o tarih/saatte başka randevusu var mı?
    boolean existsByDoctorIdAndAppointmentDate(Long doctorId, LocalDateTime appointmentDate);

    // ÇAKIŞMA KONTROLÜ: Bu hasta o saatte başka bir doktora randevu almış mı?
    boolean existsByPatientIdAndAppointmentDate(Long patientId, LocalDateTime appointmentDate);
}