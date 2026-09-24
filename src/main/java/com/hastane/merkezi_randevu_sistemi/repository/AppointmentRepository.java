package com.hastane.merkezi_randevu_sistemi.repository;
import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // KURAL R8: Hasta aynı gün aynı poliklinikten (farklı doktor olsa bile) ikinci randevu alamaz.
    // Doktor -> poliklinik ilişkisi üzerinden sorgulanır; iptal edilenler sayılmaz.
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.patient.id = :patientId
              AND a.doctor.department.id = :departmentId
              AND a.appointmentDate >= :dayStart
              AND a.appointmentDate <= :dayEnd
              AND a.status <> com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus.CANCELLED
            """)
    boolean existsSameDayAppointmentInDepartment(@Param("patientId") Long patientId,
                                                 @Param("departmentId") Long departmentId,
                                                 @Param("dayStart") LocalDateTime dayStart,
                                                 @Param("dayEnd") LocalDateTime dayEnd);

    // KURAL R9: Hastanın gelecek tarihli, iptal edilmemiş (aktif) randevu sayısı
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.patient.id = :patientId
              AND a.appointmentDate > :now
              AND a.status <> com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus.CANCELLED
            """)
    long countActiveAppointments(@Param("patientId") Long patientId, @Param("now") LocalDateTime now);

    /**
     * TEDAVİ İLİŞKİSİ: Bu doktorun bu hastayla, verilen zaman aralığında
     * iptal edilmemiş bir randevusu var mı?
     *
     * Bağlam farkındalı erişim politikasının temel koşuludur: doktor, yalnızca
     * kendisine atanmış hastanın klinik verisine erişebilir. Sorgu doktorun
     * KULLANICI kimliği üzerinden yapılır; token'da taşınan kimlik budur.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.doctor.user.id = :doctorUserId
              AND a.patient.id = :patientId
              AND a.appointmentDate >= :baslangic
              AND a.appointmentDate <= :bitis
              AND a.status <> com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus.CANCELLED
            """)
    boolean existsTedaviIliskisi(@Param("doctorUserId") Long doctorUserId,
                                 @Param("patientId") Long patientId,
                                 @Param("baslangic") LocalDateTime baslangic,
                                 @Param("bitis") LocalDateTime bitis);
}
