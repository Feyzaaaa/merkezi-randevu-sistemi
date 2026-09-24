package com.hastane.merkezi_randevu_sistemi.security;

import com.hastane.merkezi_randevu_sistemi.config.SecurityConfig;
import com.hastane.merkezi_randevu_sistemi.controller.AdminController;
import com.hastane.merkezi_randevu_sistemi.model.Role;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.service.AdminService;
import com.hastane.merkezi_randevu_sistemi.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ROL TABANLI ERİŞİM DENETİMİNİN TESTİ (HTTP seviyesinde)
 *
 * Gerçek JWT üretilip istek başlığına konur ve yetkilendirmenin filtre katmanında
 * doğru karar verdiği doğrulanır. Tezdeki üç katmanın ilk ikisi burada sınanır:
 *   - Kimlik doğrulama: token yok/geçersiz  -> 401
 *   - Yetkilendirme:    token var, rol yetersiz -> 403
 *   - Doğru rol -> 200
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "mhrs.jwt.secret=test-ortami-icin-en-az-32-byte-uzunlugunda-imzalama-anahtari",
        "mhrs.jwt.expiration-ms=3600000"
})
class AdminEndpointAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    // Yetkilendirme sınanıyor, iş mantığı değil: servisler taklit ediliyor
    @MockitoBean private AdminService adminService;
    @MockitoBean private AuditService auditService;
    // JwtAuthenticationFilter, token geçersizleştirme kontrolü için kullanıcıyı sorgular
    @MockitoBean private UserRepository userRepository;

    private String token(Long id, String email, Role rol) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(rol);
        return jwtUtil.generateToken(user);
    }

    @Test
    @DisplayName("Token'sız istek 401 döner (kimlik doğrulama)")
    void tokensizIstek401() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bozuk token 401 döner")
    void bozukToken401() throws Exception {
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer bozuk.token.degeri"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HASTA token'ı ile yönetici ucu 403 döner (yetkilendirme)")
    void hastaTokeniIle403() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + token(10L, "hasta@test.com", Role.PATIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DOKTOR token'ı ile yönetici ucu 403 döner")
    void doktorTokeniIle403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token(11L, "doktor@test.com", Role.DOCTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HASTA token'ı sistemdeki tüm randevuları göremez")
    void hastaTumRandevulariGoremez() throws Exception {
        mockMvc.perform(get("/api/admin/appointments")
                        .header("Authorization", "Bearer " + token(10L, "hasta@test.com", Role.PATIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN token'ı ile yönetici uçlarına erişilir")
    void adminTokeniIle200() throws Exception {
        org.mockito.Mockito.when(adminService.getUsers()).thenReturn(List.of());
        String adminToken = token(12L, "admin@test.com", Role.ADMIN);

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        org.mockito.Mockito.when(adminService.getAllAppointments()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/appointments").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
