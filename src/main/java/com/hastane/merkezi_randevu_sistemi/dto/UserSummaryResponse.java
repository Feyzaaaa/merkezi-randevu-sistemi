package com.hastane.merkezi_randevu_sistemi.dto;

import com.hastane.merkezi_randevu_sistemi.model.Role;
import com.hastane.merkezi_randevu_sistemi.model.User;

// Yönetici kullanıcı listesi için sadeleştirilmiş görünüm (şifre hash'i gibi alanlar hiç taşınmaz)
public class UserSummaryResponse {

    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Role role;
    // Rolü DOCTOR olan kullanıcının doctors tablosundaki karşılığı var mı?
    private final boolean hasDoctorRecord;

    public UserSummaryResponse(User user, boolean hasDoctorRecord) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.hasDoctorRecord = hasDoctorRecord;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public boolean isHasDoctorRecord() { return hasDoctorRecord; }
}
