package com.hastane.merkezi_randevu_sistemi.service;
import com.hastane.merkezi_randevu_sistemi.model.Appointment;
import com.hastane.merkezi_randevu_sistemi.model.AppointmentStatus;
import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.AppointmentRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorLeaveRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.rules.AppointmentScheduleRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AppointmentService {

    private static final DateTimeFormatter EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private DoctorLeaveRepository doctorLeaveRepository;

    private String doctorDisplayName(Doctor doctor) {
        if (doctor == null || doctor.getUser() == null) return "doktorunuz";
        String title = doctor.getTitle() != null ? doctor.getTitle() + " " : "";
        return title + doctor.getUser().getFirstName() + " " + doctor.getUser().getLastName();
    }

    private String doctorDisplayName(Appointment appointment) {
        return doctorDisplayName(appointment.getDoctor());
    }

    @Transactional
    public Appointment createAppointment(Appointment appointment) {
        // 1. NULL KONTROLÜ
        if (appointment.getDoctor() == null || appointment.getPatient() == null) {
            throw new RuntimeException("Doktor veya Hasta bilgisi eksik!");
        }

        // 2. PLANLAMA KURALLARI (geçmiş tarih, üst sınır, mesai, öğle arası, 15 dk ızgarası)
        // Bu kontrol sunucu tarafındadır: arayüz uygun olmayan saatleri listelemese de,
        // istek doğrudan API'ye gönderildiğinde tek koruma burasıdır.
        AppointmentScheduleRules.validate(appointment.getAppointmentDate(), LocalDateTime.now())
                .ifPresent(violation -> { throw new IllegalArgumentException(violation.toUserMessage()); });

        // 3. VERİYE BAĞLI PLANLAMA KURALLARI (R7-R9)
        // Bu kurallar yalnızca tarih/saate bakarak değil, veritabanındaki mevcut
        // kayıtlara bakarak karar verilir; bu yüzden kural sınıfında değil burada uygulanır.
        LocalDateTime requested = appointment.getAppointmentDate();
        Long doctorId = appointment.getDoctor().getId();
        Long patientId = appointment.getPatient().getId();

        // R7: Doktor o gün izinli/görevde değilse randevu açılamaz
        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctorId, requested.toLocalDate())) {
            throw new IllegalArgumentException(AppointmentScheduleRules.Violation.DOCTOR_ON_LEAVE.toUserMessage());
        }

        // R8: Aynı gün aynı poliklinikten ikinci randevu (doktor farklı olsa bile)
        Doctor requestedDoctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Seçilen doktor bulunamadı!"));
        if (requestedDoctor.getDepartment() != null
                && appointmentRepository.existsSameDayAppointmentInDepartment(
                        patientId,
                        requestedDoctor.getDepartment().getId(),
                        requested.toLocalDate().atStartOfDay(),
                        requested.toLocalDate().atTime(LocalTime.MAX))) {
            throw new IllegalArgumentException(AppointmentScheduleRules.Violation.SAME_DAY_SAME_DEPARTMENT.toUserMessage());
        }

        // R9: Aktif randevu üst sınırı
        if (appointmentRepository.countActiveAppointments(patientId, LocalDateTime.now())
                >= AppointmentScheduleRules.MAX_ACTIVE_APPOINTMENTS) {
            throw new IllegalArgumentException(AppointmentScheduleRules.Violation.ACTIVE_LIMIT_REACHED.toUserMessage());
        }

        // 4. Doktor Çakışma Kontrolü (iptal edilmiş randevular çakışma sayılmaz)
        // NOT: Bu kontrol "önce bak, sonra kaydet" mantığıdır — tek başına eşzamanlı istekler
        // için yeterli değildir. Asıl garanti aşağıdaki veritabanı unique index'inden gelir
        // (bkz. schema.sql); bu kontrol sadece normal durumda hızlı ve dostane bir mesaj verir.
        if (appointmentRepository.existsByDoctorIdAndAppointmentDateAndStatusNot(
                appointment.getDoctor().getId(), appointment.getAppointmentDate(), AppointmentStatus.CANCELLED)) {
            throw new RuntimeException("Bu doktorun bu saatte randevusu zaten dolu!");
        }

        // 5. Hasta Çakışma Kontrolü (Tez konun!) (iptal edilmiş randevular çakışma sayılmaz)
        if (appointmentRepository.existsByPatientIdAndAppointmentDateAndStatusNot(
                appointment.getPatient().getId(), appointment.getAppointmentDate(), AppointmentStatus.CANCELLED)) {
            throw new RuntimeException("Aynı saatte başka bir randevunuz zaten bulunuyor!");
        }

        appointment.setStatus(AppointmentStatus.PENDING);

        // 6. SON GÜVENCE: İki istek yukarıdaki kontrolleri tam olarak aynı anda geçip buraya
        // birlikte ulaşırsa (klasik yarış durumu / race condition), veritabanındaki kısmi
        // unique index ikinci INSERT'i reddeder. Bunu burada yakalayıp aynı dostane mesajlara çeviriyoruz.
        try {
            Appointment saved = appointmentRepository.save(appointment);

            // ÖNEMLİ: appointment.getPatient()/getDoctor(), istekten geldiği haliyle (sadece id dolu)
            // kalır ve aynı transaction içinde Appointment'ı tekrar sorgulasak bile Hibernate'in
            // 1. seviye önbelleği yüzünden hâlâ aynı eksik nesneyi görürüz. Bu yüzden e-posta için
            // gereken User/Doctor'ı DOĞRUDAN kendi repository'lerinden taze çekiyoruz.
            User patientUser = userRepository.findById(saved.getPatient().getId()).orElse(null);
            Doctor doctorFull = doctorRepository.findById(saved.getDoctor().getId()).orElse(null);
            if (patientUser != null) {
                emailService.send(
                        patientUser.getEmail(),
                        "Randevunuz Oluşturuldu - MHRS",
                        "Sayın " + patientUser.getFirstName() + ",\n\n" +
                                doctorDisplayName(doctorFull) + " ile " + saved.getAppointmentDate().format(EMAIL_DATE_FORMAT) +
                                " tarihli randevunuz başarıyla oluşturulmuştur.\n\nMHRS - Merkezi Sağlık Sistemi"
                );
            }
            return saved;
        } catch (DataIntegrityViolationException e) {
            String rootMessage = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : "";
            if (rootMessage.contains("ux_appointments_doctor_slot_active")) {
                throw new RuntimeException("Bu doktorun bu saatte randevusu zaten dolu!");
            } else if (rootMessage.contains("ux_appointments_patient_slot_active")) {
                throw new RuntimeException("Aynı saatte başka bir randevunuz zaten bulunuyor!");
            }
            throw new RuntimeException("Bu saat diliminde bir çakışma oluştu, lütfen tekrar deneyin.");
        }
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    public Appointment getById(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Randevu bulunamadı: " + appointmentId));
    }

    public List<Appointment> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorId(doctorId);
    }

    public List<Appointment> getAppointmentsByPatient(Long patientId) {
        return appointmentRepository.findByPatientId(patientId);
    }

    public Appointment saveClinicalNote(Long appointmentId, String note) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Randevu bulunamadı: " + appointmentId));
        appointment.setNote(note);
        return appointmentRepository.save(appointment);
    }

    /**
     * @param enforceNoticePeriod hasta kendi randevusunu iptal ediyorsa true verilir ve
     *        son dakika iptali (R10) engellenir. Doktor ve yönetici için false'tur:
     *        onlar operasyonel gerekçeyle her an iptal edebilmelidir.
     */
    public Appointment cancelAppointment(Long appointmentId, boolean enforceNoticePeriod) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Randevu bulunamadı: " + appointmentId));

        // R10: Randevuya çok az kalmışsa hasta iptal edemez
        if (enforceNoticePeriod && appointment.getAppointmentDate() != null
                && appointment.getAppointmentDate().isBefore(
                        LocalDateTime.now().plusHours(AppointmentScheduleRules.CANCELLATION_NOTICE_HOURS))) {
            throw new IllegalArgumentException(AppointmentScheduleRules.Violation.CANCELLATION_TOO_LATE.toUserMessage());
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new RuntimeException("Bu randevu zaten iptal edilmiş!");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new RuntimeException("Tamamlanmış bir randevu iptal edilemez!");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment cancelled = appointmentRepository.save(appointment);

        emailService.send(
                cancelled.getPatient().getEmail(),
                "Randevunuz İptal Edildi - MHRS",
                "Sayın " + cancelled.getPatient().getFirstName() + ",\n\n" +
                        doctorDisplayName(cancelled) + " ile " + cancelled.getAppointmentDate().format(EMAIL_DATE_FORMAT) +
                        " tarihli randevunuz iptal edilmiştir.\n\nMHRS - Merkezi Sağlık Sistemi"
        );

        return cancelled;
    }

    // --- SLOT ÜRETİMİ: kurallar AppointmentScheduleRules sınıfından okunur ---
    // Aynı kural kümesi createAppointment'ta da uygulanır; burada sadece kullanıcıya
    // gösterilecek listeyi süzeriz (geçmiş saatler, öğle arası, mesai dışı).
    public List<String> getAvailableSlots(Long doctorId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        List<String> allSlots = new ArrayList<>();

        // R7: Doktor o gün izinliyse hiç saat sunulmaz
        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctorId, date)) {
            return allSlots;
        }

        LocalTime start = AppointmentScheduleRules.WORK_START;
        while (!start.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES).isAfter(AppointmentScheduleRules.WORK_END)) {
            // Kurala uymayan dilimler (öğle arası, bugünün geçip gitmiş saatleri, tarih üst sınırı) listelenmez
            if (AppointmentScheduleRules.isSelectable(date.atTime(start), now)) {
                allSlots.add(start + " - " + start.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES));
            }
            start = start.plusMinutes(AppointmentScheduleRules.SLOT_MINUTES);
        }

        // Bu doktora ait o günkü dolu randevuların başlangıç saatlerini çıkar (senaryo tabanlı çakışma önleme)
        List<Appointment> dayAppointments = appointmentRepository.findByDoctorIdAndAppointmentDateBetween(
                doctorId, date.atStartOfDay(), date.atTime(LocalTime.MAX));
        Set<String> bookedStartTimes = new HashSet<>();
        for (Appointment appointment : dayAppointments) {
            if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
                bookedStartTimes.add(appointment.getAppointmentDate().toLocalTime().toString());
            }
        }
        allSlots.removeIf(slot -> bookedStartTimes.contains(slot.split(" - ")[0]));

        return allSlots;
    }
}
