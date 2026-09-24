package com.hastane.merkezi_randevu_sistemi.model;

/**
 * DENETLENEN İŞLEM TÜRLERİ
 *
 * Rol tabanlı yetkilendirmede "kim neyi yapabilir" kadar önemli olan ikinci soru
 * "kim neyi yaptı" sorusudur. Yetkinin kötüye kullanımı ancak iz kaydıyla tespit
 * edilebilir; bu enum izlenen olayları tanımlar.
 */
public enum AuditAction {

    LOGIN_SUCCESS("Başarılı giriş"),
    LOGIN_FAILED("Başarısız giriş denemesi"),
    ACCOUNT_LOCKED("Hesap geçici olarak kilitlendi"),
    REGISTER("Yeni hasta kaydı"),
    PASSWORD_CHANGED("Şifre değiştirildi"),
    PASSWORD_CHANGE_FAILED("Hatalı mevcut şifreyle değiştirme denemesi"),

    ROLE_CHANGED("Kullanıcı rolü değiştirildi"),
    DOCTOR_CREATED("Doktor tanımlandı"),
    DEPARTMENT_CREATED("Poliklinik tanımlandı"),

    // Klinik veri erişimi: sağlık verisi hassas olduğu için hem verilen hem
    // reddedilen erişimler kaydedilir (erişim günlüğü / access log).
    CLINICAL_ACCESS_GRANTED("Klinik veriye erişildi"),
    CLINICAL_ACCESS_DENIED("Klinik veri erişimi reddedildi"),
    CLINICAL_ACCESS_EMERGENCY("ACİL ERİŞİM — politika aşıldı"),

    APPOINTMENT_CANCELLED("Randevu iptal edildi"),
    APPOINTMENT_STATUS_CHANGED("Randevu durumu değiştirildi");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
