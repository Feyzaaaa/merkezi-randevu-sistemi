package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.model.Doctor;
import com.hastane.merkezi_randevu_sistemi.model.DoctorLeave;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorLeaveRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class DoctorService {

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorLeaveRepository doctorLeaveRepository;

    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    public Optional<Doctor> getDoctorByUserId(Long userId) {
        return doctorRepository.findByUserId(userId);
    }


    // --- DOKTOR İZİN / GÖREV GÜNLERİ (kural R7) ---

    /** Doktorun bugünden itibaren olan izin günleri (geçmiş izinler listelenmez) */
    public List<DoctorLeave> getUpcomingLeaves(Long doctorId) {
        return doctorLeaveRepository.findByDoctorIdAndLeaveDateGreaterThanEqualOrderByLeaveDateAsc(doctorId, LocalDate.now());
    }

    /**
     * Doktora izin günü ekler. O gün için zaten randevu verilmişse izin tanımlanmaz:
     * mevcut randevuların önce iptal edilmesi gerekir ki hasta mağdur olmasın.
     */
    public DoctorLeave addLeave(Long doctorId, LocalDate date, String reason, boolean hasAppointmentsThatDay) {
        if (date == null) {
            throw new IllegalArgumentException("İzin tarihi belirtilmedi!");
        }
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Geçmiş bir güne izin tanımlanamaz!");
        }
        if (doctorLeaveRepository.existsByDoctorIdAndLeaveDate(doctorId, date)) {
            throw new IllegalArgumentException("Bu gün için zaten izin tanımlı!");
        }
        if (hasAppointmentsThatDay) {
            throw new IllegalArgumentException(
                    "Bu günde randevulu hastanız var. Önce randevuları iptal edin, sonra izin tanımlayın.");
        }

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doktor bulunamadı: " + doctorId));

        DoctorLeave leave = new DoctorLeave();
        leave.setDoctor(doctor);
        leave.setLeaveDate(date);
        leave.setReason(reason == null || reason.isBlank() ? "İzinli" : reason.trim());
        return doctorLeaveRepository.save(leave);
    }

    public void removeLeave(Long doctorId, Long leaveId) {
        DoctorLeave leave = doctorLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("İzin kaydı bulunamadı: " + leaveId));
        if (!leave.getDoctor().getId().equals(doctorId)) {
            throw new IllegalArgumentException("Yalnızca kendi izin günlerinizi kaldırabilirsiniz!");
        }
        doctorLeaveRepository.delete(leave);
    }
}
