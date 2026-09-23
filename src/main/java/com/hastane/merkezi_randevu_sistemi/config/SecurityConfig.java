package com.hastane.merkezi_randevu_sistemi.config;

import com.hastane.merkezi_randevu_sistemi.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.io.IOException;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/users/login", "/api/users/register").permitAll()

                // --- YÖNETİCİ (ADMIN) YETKİLERİ ---
                // Sistem geneli veriler ve tanımlama işlemleri yalnızca ADMIN rolüne açıktır.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Tüm kullanıcı listesi: kişisel veri içerdiği için sadece yönetici görebilir
                .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                // Sistemdeki TÜM randevular (tek hastanın değil): sadece yönetici denetimi
                .requestMatchers(HttpMethod.GET, "/api/appointments").hasRole("ADMIN")
                // Doktor ve poliklinik tanımlamak bir yönetim işlemidir
                .requestMatchers(HttpMethod.POST, "/api/doctors").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/departments").hasRole("ADMIN")

                // --- DOKTOR YETKİLERİ ---
                // Doktor kendi Doctor kaydını (doctorId'sini) bulmak için kullanır
                .requestMatchers("/api/doctors/by-user/**").hasRole("DOCTOR")
                // İzin/görev günleri: doktor yalnızca kendi kaydı için yönetir (sahiplik controller'da)
                .requestMatchers("/api/doctors/*/leaves/**").hasRole("DOCTOR")
                .requestMatchers("/api/doctors/*/leaves").hasRole("DOCTOR")
                // Doktor Portalı: sadece DOCTOR rolü kendi hasta listesini görebilir ve vaka notu girebilir
                .requestMatchers("/api/appointments/doctor/**").hasRole("DOCTOR")
                .requestMatchers(HttpMethod.PATCH, "/api/appointments/*/note").hasRole("DOCTOR")

                // --- ORTAK / HASTA YETKİLERİ ---
                // Randevu iptali: hasta ve doktor kendi randevusunu, yönetici ise denetim amacıyla her randevuyu iptal edebilir
                .requestMatchers(HttpMethod.PATCH, "/api/appointments/*/cancel").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                // Hasta Portalı: sadece PATIENT rolü kendi randevu geçmişini görebilir ve randevu alabilir
                .requestMatchers("/api/appointments/patient/**").hasRole("PATIENT")
                .requestMatchers(HttpMethod.POST, "/api/appointments").hasRole("PATIENT")
                // Hasta Profili (boy/kilo/yaş/cinsiyet/kan grubu/alerji/iletişim): sadece PATIENT
                .requestMatchers("/api/patient-profiles/**").hasRole("PATIENT")
                // Laboratuvar Sonuçları: hasta kendi sonuçlarını görür, doktor sonuç ekler
                .requestMatchers(HttpMethod.GET, "/api/lab-results/patient/**").hasAnyRole("PATIENT", "DOCTOR")
                .requestMatchers(HttpMethod.POST, "/api/lab-results").hasRole("DOCTOR")
                // Diğer her şey (poliklinik/doktor listesi, boş saatler): en azından geçerli bir token şart
                .anyRequest().authenticated()
            )
            // Frontend'in "oturum bitti" ile "bu role kapalı" durumlarını ayırt edebilmesi için:
            // token yoksa/geçersizse 401, token geçerli ama rol yetersizse 403 döneriz.
            // NOT: Spring Security sürümüne göre rol reddi bazen entry point'e, bazen
            // accessDeniedHandler'a düşer. Bu yüzden kararı sürüme bırakmayıp her iki
            // uçta da isteğin geçerli bir token taşıyıp taşımadığına bakarak veriyoruz.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> sendAuthFailure(request, response))
                .accessDeniedHandler((request, response, accessDeniedException) -> sendAuthFailure(request, response))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }

    // Geçerli token taşıyan istek yetki hatası alıyorsa 403 (yetkilendirme),
    // token hiç yoksa veya çözülemiyorsa 401 (kimlik doğrulama) döner.
    private static void sendAuthFailure(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_ATTRIBUTE))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bu işlem için yetkiniz yok");
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Oturum bulunamadı veya süresi dolmuş");
        }
    }

    // Şifreleri hashlemek/doğrulamak için: register'da encode, login'de matches ile kontrol edilir
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // MİLYONLARCA FARKLI IP/CİHAZ İÇİN: Tüm origin desenlerine izin veriyoruz
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
