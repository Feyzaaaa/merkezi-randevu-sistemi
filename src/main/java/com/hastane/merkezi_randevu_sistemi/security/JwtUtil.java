package com.hastane.merkezi_randevu_sistemi.security;

import com.hastane.merkezi_randevu_sistemi.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// Rol tabanlı yetkilendirme için: kullanıcının kimliğini ve rolünü taşıyan imzalı token üretir/okur
@Component
public class JwtUtil {

    @Value("${mhrs.jwt.secret}")
    private String secret;

    @Value("${mhrs.jwt.expiration-ms}")
    private long expirationMs;

    // HMAC-SHA256 için anahtar en az 256 bit, yani 32 bayt olmalıdır.
    private static final int EN_AZ_ANAHTAR_UZUNLUGU = 32;

    /**
     * Uygulama açılırken imzalama anahtarını doğrular.
     *
     * Anahtar yoksa ya da çok kısaysa uygulama BAŞLAMAZ. Bu bilinçli bir tercihtir:
     * zayıf veya varsayılan bir anahtarla sessizce çalışmak, token sahteciliğine
     * (istediği rolü kendine atayan kullanıcıya) kapı açardı. Rol tabanlı
     * yetkilendirmenin tamamı bu anahtarın gizliliğine dayanır.
     */
    @PostConstruct
    void anahtariDogrula() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "mhrs.jwt.secret tanımlı değil. Geliştirmede application-local.properties, "
                  + "canlıda MHRS_JWT_SECRET ortam değişkeni ile verilmelidir.");
        }
        int uzunluk = secret.getBytes(StandardCharsets.UTF_8).length;
        if (uzunluk < EN_AZ_ANAHTAR_UZUNLUGU) {
            throw new IllegalStateException(
                    "mhrs.jwt.secret en az " + EN_AZ_ANAHTAR_UZUNLUGU + " bayt olmalıdır, verilen: " + uzunluk);
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    // Geçersiz/süresi dolmuş token'da JwtException fırlatır; çağıran (filter) bunu yakalayıp isteği kimliksiz devam ettirir
    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
