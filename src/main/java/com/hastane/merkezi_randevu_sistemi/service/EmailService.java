package com.hastane.merkezi_randevu_sistemi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Randevu bildirimleri için e-posta gönderir. E-posta gönderimi başarısız olsa bile
// asıl işlemi (randevu oluşturma/iptal) engellememesi için hata burada yutulur, sadece loglanır.
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    // Gmail, kimliği doğrulanan hesap dışında bir "From" adresine izin vermiyor;
    // bu yüzden gönderen adresi her zaman SMTP kullanıcı adıyla aynı olmalı.
    @Value("${spring.mail.username}")
    private String fromAddress;

    // @Async: SMTP bağlantısı/gönderimi arka planda bir thread'de yapılır, HTTP isteğini bloklamaz.
    // Yük testinde randevu oluşturmanın e-posta gönderimi yüzünden yavaşladığını gördük — bu düzeltir.
    @Async
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("⚠️ E-posta gönderilemedi (" + to + "): " + e.getMessage());
        }
    }
}
