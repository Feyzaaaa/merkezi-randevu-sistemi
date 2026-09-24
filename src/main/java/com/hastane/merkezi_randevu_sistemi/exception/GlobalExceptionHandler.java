package com.hastane.merkezi_randevu_sistemi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * MERKEZİ HATA YÖNETİMİ
 *
 * ResponseEntityExceptionHandler'dan türetilmiştir: Spring'in kendi ürettiği
 * anlamlı HTTP hataları (405 Method Not Allowed, 404, 415 Unsupported Media Type,
 * gövde ayrıştırma hataları...) kendi doğru kodlarıyla döner.
 *
 * Bu türetme olmadan, aşağıdaki genel Exception yakalayıcısı bu hataları da
 * yutup hepsini 500 Sunucu Hatası yapıyordu: var olmayan bir uca yapılan istek
 * "405 Method Not Allowed" yerine "500" görünüyordu.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // İş kuralı ihlalleri (geçersiz rol, tekrar eden tanım, eksik alan) sunucu hatası değildir:
    // frontend'in mesajı kullanıcıya gösterebilmesi için 400 Bad Request olarak döner.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // Beklenmeyen hatalar: son çare
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        System.err.println("🔥 HATA OLUŞTU: " + e.getMessage());
        return new ResponseEntity<>("Sunucu Hatası: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
