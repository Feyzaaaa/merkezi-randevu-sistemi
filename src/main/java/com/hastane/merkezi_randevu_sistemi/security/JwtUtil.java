package com.hastane.merkezi_randevu_sistemi.security;

import com.hastane.merkezi_randevu_sistemi.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
