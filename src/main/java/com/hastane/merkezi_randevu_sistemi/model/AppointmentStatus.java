package com.hastane.merkezi_randevu_sistemi.model;

public enum AppointmentStatus {
    PENDING,   // Randevu alındı, henüz onaylanmadı
    CONFIRMED, // Randevu onaylandı
    CANCELLED, // Randevu iptal edildi
    COMPLETED  // Muayene gerçekleşti ve bitti
}