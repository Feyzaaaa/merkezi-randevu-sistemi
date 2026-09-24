package com.hastane.merkezi_randevu_sistemi.policy;

import com.hastane.merkezi_randevu_sistemi.repository.AppointmentRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorLeaveRepository;
import com.hastane.merkezi_randevu_sistemi.repository.DoctorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * BAĞLAM FARKINDALI KLİNİK ERİŞİM POLİTİKASI (ABAC)
 *
 * Rol tabanlı yetkilendirme "DOCTOR rolü tahlil sonucu okuyabilir" der. Bu yeterli
 * değildir: hastanın klinik verisi, hastanede çalışan HER doktora açık olmamalıdır.
 * Bu politika, rolün üstüne bağlamsal koşullar ekler:
 *
 *   K1  TEDAVİ İLİŞKİSİ  Doktorun bu hastayla, tanımlı zaman penceresinde
 *                        iptal edilmemiş bir randevusu olmalı.
 *   K2  MESAİ PENCERESİ  Erişim anı doktorun mesai saatleri içinde olmalı.
 *   K3  GÖREVDE OLMA     Doktor o gün izinli/görevde olmamalı.
 *
 * Üç koşul da sağlanırsa erişim verilir. Aksi hâlde reddedilir ve red gerekçesi
 * hem kullanıcıya hem denetim kaydına yazılır.
 *
 * ACİL ERİŞİM ("kırıl-camı"):
 * Katı bir politika gerçek bir acil durumda hastaya zarar verebilir — mesai dışında
 * gelen bir hastanın tahlilini doktorun görememesi kabul edilebilir değildir. Bu
 * yüzden gerekçe bildirmek koşuluyla politikanın aşılmasına izin verilir. Erişim
 * ENGELLENMEZ, HESAP SORULUR: her aşım denetim kaydına ayrı bir işlem türü olarak,
 * gerekçesiyle birlikte yazılır ve yönetici panelinde görünür.
 *
 * Bu tasarım, güvenlik politikalarının "her şeyi kilitle" değil, "erişimi bağlama
 * göre daralt ve istisnaları hesap verebilir kıl" ilkesine dayanır.
 */
@Service
public class ClinicalAccessPolicy {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final ClinicalAccessProperties ayarlar;

    public ClinicalAccessPolicy(AppointmentRepository appointmentRepository,
                                DoctorRepository doctorRepository,
                                DoctorLeaveRepository doctorLeaveRepository,
                                ClinicalAccessProperties ayarlar) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.doctorLeaveRepository = doctorLeaveRepository;
        this.ayarlar = ayarlar;
    }

    /**
     * Politikanın kararı. Yalnızca izin/ret değil, her koşulun sonucunu da taşır;
     * böylece red gerekçesi kullanıcıya açıklanabilir ve denetim kaydına yazılabilir.
     */
    public record Karar(
            boolean izinli,
            boolean acilErisim,
            boolean tedaviIliskisiVar,
            boolean mesaiIcinde,
            boolean gorevde,
            String sebep
    ) {
        /** Denetim kaydına yazılacak tek satırlık özet */
        public String denetimOzeti() {
            return "ilişki=%s mesai=%s görevde=%s%s".formatted(
                    tedaviIliskisiVar ? "var" : "yok",
                    mesaiIcinde ? "içinde" : "dışında",
                    gorevde ? "evet" : "hayır",
                    acilErisim ? " [ACİL ERİŞİM]" : "");
        }
    }

    /**
     * Bir doktorun bir hastanın klinik verisine erişip erişemeyeceğine karar verir.
     *
     * @param doctorUserId erişmek isteyen doktorun kullanıcı kimliği
     * @param patientId    verisine erişilmek istenen hasta
     * @param simdi        erişim anı (test edilebilirlik için dışarıdan verilir)
     * @param acilGerekce  acil erişim gerekçesi; yoksa null
     */
    public Karar degerlendir(Long doctorUserId, Long patientId, LocalDateTime simdi, String acilGerekce) {
        boolean iliski = tedaviIliskisiVarMi(doctorUserId, patientId, simdi);
        boolean mesai = mesaiIcindeMi(simdi.toLocalTime());
        boolean gorevde = gorevdeMi(doctorUserId, simdi);

        if (iliski && mesai && gorevde) {
            return new Karar(true, false, true, true, true, "Erişim verildi");
        }

        // --- Politika sağlanmadı: acil erişim devreye girebilir mi? ---
        if (acilGerekceGecerliMi(acilGerekce)) {
            return new Karar(true, true, iliski, mesai, gorevde,
                    "ACİL ERİŞİM — gerekçe: " + acilGerekce.trim());
        }

        return new Karar(false, false, iliski, mesai, gorevde, redSebebi(iliski, mesai, gorevde));
    }

    /** Acil erişim gerekçesi olmadan, yalnızca politikayı sorgular */
    public Karar degerlendir(Long doctorUserId, Long patientId, LocalDateTime simdi) {
        return degerlendir(doctorUserId, patientId, simdi, null);
    }

    // --- KOŞULLAR ---

    /** K1: Doktorun bu hastayla, tanımlı pencerede iptal edilmemiş randevusu var mı? */
    private boolean tedaviIliskisiVarMi(Long doctorUserId, Long patientId, LocalDateTime simdi) {
        if (doctorUserId == null || patientId == null) return false;
        return appointmentRepository.existsTedaviIliskisi(
                doctorUserId,
                patientId,
                simdi.minusDays(ayarlar.getIliskiGecmisGun()),
                simdi.plusDays(ayarlar.getIliskiGelecekGun()));
    }

    /** K2: Erişim anı mesai penceresinde mi? */
    private boolean mesaiIcindeMi(LocalTime saat) {
        return !saat.isBefore(ayarlar.getMesaiBaslangic()) && saat.isBefore(ayarlar.getMesaiBitis());
    }

    /** K3: Doktor o gün izinli/görevde değil mi? */
    private boolean gorevdeMi(Long doctorUserId, LocalDateTime simdi) {
        return doctorRepository.findByUserId(doctorUserId)
                .map(doktor -> !doctorLeaveRepository.existsByDoctorIdAndLeaveDate(
                        doktor.getId(), simdi.toLocalDate()))
                .orElse(false); // doktor kaydı yoksa klinik veriye erişemez
    }

    private boolean acilGerekceGecerliMi(String gerekce) {
        return ayarlar.isAcilErisimAcik()
                && gerekce != null
                && gerekce.trim().length() >= ayarlar.getAcilGerekceMinUzunluk();
    }

    /** Reddin nedenini kullanıcıya anlaşılır biçimde açıklar */
    private String redSebebi(boolean iliski, boolean mesai, boolean gorevde) {
        if (!iliski) {
            return "Bu hasta size atanmamış. Klinik verilere yalnızca tedavisini üstlendiğiniz "
                    + "hastalar için erişebilirsiniz.";
        }
        if (!gorevde) {
            return "Bugün izinli görünüyorsunuz; klinik verilere erişim kapalıdır.";
        }
        if (!mesai) {
            return "Mesai saatleri dışındasınız (%s - %s). Klinik verilere erişim kapalıdır."
                    .formatted(ayarlar.getMesaiBaslangic(), ayarlar.getMesaiBitis());
        }
        return "Erişim reddedildi.";
    }

    /** Acil erişimin açık olup olmadığını ve gerekçe koşulunu arayüze bildirmek için */
    public ClinicalAccessProperties getAyarlar() {
        return ayarlar;
    }
}
