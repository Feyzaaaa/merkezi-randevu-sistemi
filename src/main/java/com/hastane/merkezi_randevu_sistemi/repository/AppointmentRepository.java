package com.hastane.merkezi_randevu_sistemi.repository;
import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;
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

    // Bir doktorun belirli bir gün aralığındaki randevuları (müsait saatleri filtrelemek için)
    List<Appointment> findByDoctorIdAndAppointmentDateBetween(Long doctorId, LocalDateTime start, LocalDateTime end);

    // ÇAKIŞMA KONTROLÜ: Bu doktorun o tarih/saatte İPTAL EDİLMEMİŞ başka randevusu var mı?
    // (status hariç tutuluyor ki iptal edilen bir randevu saati tekrar müsait hale gelsin)
    boolean existsByDoctorIdAndAppointmentDateAndStatusNot(Long doctorId, LocalDateTime appointmentDate, AppointmentStatus status);

    // ÇAKIŞMA KONTROLÜ: Bu hasta o saatte İPTAL EDİLMEMİŞ başka bir doktora randevu almış mı?
    boolean existsByPatientIdAndAppointmentDateAndStatusNot(Long patientId, LocalDateTime appointmentDate, AppointmentStatus status);

    // Yönetici paneli: randevu durum dağılımı (PENDING/CONFIRMED/COMPLETED/CANCELLED)
    long countByStatus(AppointmentStatus status);

    // Yönetici paneli: belirli bir gün aralığındaki tüm randevular (bugünün yoğunluğu)
    List<Appointment> findByAppointmentDateBetween(LocalDateTime start, LocalDateTime end);

    // Yönetici paneli: sistemdeki tüm randevular, en yakın tarih en üstte
    List<Appointment> findAllByOrderByAppointmentDateDesc();
}
