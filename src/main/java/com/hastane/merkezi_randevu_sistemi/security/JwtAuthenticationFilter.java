package com.hastane.merkezi_randevu_sistemi.security;

import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// Authorization: Bearer <jwt> başlığını okuyup SecurityContext'e rolüyle birlikte kimlik doldurur
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Bu istek geçerli bir token taşıyordu mu? SecurityConfig, 401 (kimlik yok) ile
    // 403 (kimlik var ama rol yetersiz) ayrımını bu işarete bakarak yapar.
    public static final String AUTHENTICATED_ATTRIBUTE = "mhrs.authenticated";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseClaims(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);
                Long userId = claims.get("userId", Long.class);

                // TOKEN GEÇERSİZLEŞTİRME
                // JWT durumsuzdur: üretildikten sonra sunucuda saklanmaz, tek tek iptal edilemez.
                // Kullanıcı şifresini değiştirdiğinde, o andan ÖNCE üretilmiş token'lar
                // (çalınmış olabilecek eski oturumlar dahil) burada reddedilir.
                if (tokenSifreDegisikligindenEski(userId, claims)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                // Principal olarak sadece email değil, userId'yi de taşıyoruz ki controller'lar
                // SAHİPLİK kontrolü yapabilsin (örn. "bu randevu gerçekten bu kullanıcıya mı ait?")
                AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);

                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
            } catch (JwtException | IllegalArgumentException e) {
                // Token bozuk/süresi dolmuş: kimliksiz devam ettiriyoruz, aşağıdaki authorization kuralları reddeder
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Token, kullanıcının son şifre değişikliğinden önce mi üretilmiş?
     * Şifre hiç değiştirilmemişse (damga yok) kontrol uygulanmaz.
     */
    private boolean tokenSifreDegisikligindenEski(Long userId, Claims claims) {
        if (userId == null || claims.getIssuedAt() == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> {
                    LocalDateTime degisim = user.getPasswordChangedAt();
                    if (degisim == null) return false;
                    LocalDateTime uretim = claims.getIssuedAt().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime();
                    // JWT "iat" alanı saniye hassasiyetindedir; aynı saniyede üretilen
                    // token'ın yanlışlıkla elenmemesi için bir saniye pay bırakıyoruz.
                    return uretim.isBefore(degisim.minusSeconds(1));
                })
                .orElse(false);
    }
}
