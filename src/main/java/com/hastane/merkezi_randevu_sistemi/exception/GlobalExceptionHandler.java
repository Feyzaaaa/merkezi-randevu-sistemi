package com.hastane.merkezi_randevu_sistemi.exception;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    // İş kuralı ihlalleri (geçersiz rol, tekrar eden tanım, eksik alan) sunucu hatası değildir:
    // frontend'in mesajı kullanıcıya gösterebilmesi için 400 Bad Request olarak döner.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        System.err.println("🔥 HATA OLUŞTU: " + e.getMessage());
        return new ResponseEntity<>("Sunucu Hatası: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}