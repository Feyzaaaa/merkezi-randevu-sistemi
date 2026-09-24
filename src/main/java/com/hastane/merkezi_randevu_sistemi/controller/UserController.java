package com.hastane.merkezi_randevu_sistemi.controller;
import com.hastane.merkezi_randevu_sistemi.dto.AuthResponse;
import com.hastane.merkezi_randevu_sistemi.dto.LoginRequest;
import com.hastane.merkezi_randevu_sistemi.model.AuditAction;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.model.Role; // Role enum'ını import etmeyi unutma!
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.rules.PasswordPolicy;
import com.hastane.merkezi_randevu_sistemi.security.JwtUtil;
import com.hastane.merkezi_randevu_sistemi.service.AuditService;
import com.hastane.merkezi_randevu_sistemi.service.LoginAttemptService;
import com.hastane.merkezi_randevu_sistemi.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(
    origins = "*",
    allowedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditService auditService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    // Tüm kullanıcı listesi: SecurityConfig'te ADMIN rolüne kilitlidir
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user, HttpServletRequest request) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body("E-posta adresi zorunludur!");
        }

        // 1. Email kontrolü
        if (userRepository.findByEmailIgnoreCase(user.getEmail().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Bu e-posta adresi zaten kullanımda!");
        }

        // 2. ŞİFRE POLİTİKASI: yetkilendirme, en zayıf şifre kadar güçlüdür
        Optional<String> policyError = PasswordPolicy.validate(user.getPassword());
        if (policyError.isPresent()) {
            return ResponseEntity.badRequest().body(policyError.get());
        }

        // 3. Rol istemciden gelemez: kayıt olan herkes hastadır.
        //    (Aksi hâlde istek gövdesine "role":"ADMIN" yazan herkes yönetici olurdu.)
        user.setRole(Role.PATIENT);

        // 4. Şifreyi düz metin değil, BCrypt hash'i olarak sakla
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        User savedUser = userService.saveUser(user);
        auditService.recordAnonymous(AuditAction.REGISTER, savedUser.getEmail(),
                "Yeni hasta kaydı oluşturuldu", true, request);

        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body("E-posta veya şifre boş olamaz!");
        }

        String cleanEmailInput = loginRequest.getEmail().trim();
        String passwordInput = loginRequest.getPassword();

        Optional<User> found = userRepository.findByEmailIgnoreCase(cleanEmailInput);

        // Kullanıcı yoksa: hangi e-postaların kayıtlı olduğunu sızdırmamak için
        // şifre hatasıyla aynı mesajı döneriz (kullanıcı sayımı saldırısına karşı).
        if (found.isEmpty()) {
            auditService.recordAnonymous(AuditAction.LOGIN_FAILED, cleanEmailInput,
                    "Kayıtlı olmayan e-posta ile giriş denemesi", false, request);
            return ResponseEntity.status(401).body("E-posta veya şifre hatalı!");
        }

        User user = found.get();

        // KİLİT KONTROLÜ: eşiği aşmış hesap geçici olarak kapalıdır
        if (loginAttemptService.isLocked(user)) {
            auditService.recordAnonymous(AuditAction.LOGIN_FAILED, cleanEmailInput,
                    "Kilitli hesaba giriş denemesi", false, request);
            return ResponseEntity.status(423).body(
                    "Çok fazla hatalı deneme nedeniyle hesabınız geçici olarak kilitlendi. "
                            + loginAttemptService.remainingLockMinutes(user) + " dakika sonra tekrar deneyin.");
        }

        if (!passwordEncoder.matches(passwordInput, user.getPassword())) {
            boolean kilitlendi = loginAttemptService.recordFailure(user);
            auditService.recordAnonymous(AuditAction.LOGIN_FAILED, cleanEmailInput,
                    "Hatalı şifre", false, request);

            if (kilitlendi) {
                auditService.recordAnonymous(AuditAction.ACCOUNT_LOCKED, cleanEmailInput,
                        LoginAttemptService.MAX_FAILED_ATTEMPTS + " ardışık hatalı denemeden sonra "
                                + LoginAttemptService.LOCK_MINUTES + " dakika kilitlendi", false, request);
                return ResponseEntity.status(423).body(
                        "Çok fazla hatalı deneme! Hesabınız " + LoginAttemptService.LOCK_MINUTES
                                + " dakika süreyle kilitlendi.");
            }

            int kalan = loginAttemptService.remainingAttempts(user);
            return ResponseEntity.status(401).body(
                    "E-posta veya şifre hatalı! (Kalan deneme hakkı: " + kalan + ")");
        }

        // Başarılı giriş: sayaç sıfırlanır, kimlik ve rol taşıyan token üretilir
        loginAttemptService.recordSuccess(user);
        auditService.recordAnonymous(AuditAction.LOGIN_SUCCESS, user.getEmail(),
                "Rol: " + user.getRole(), true, request);

        String token = jwtUtil.generateToken(user);
        AuthResponse authResponse = new AuthResponse(
                user.getId(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getRole(), token);
        return ResponseEntity.ok(authResponse);
    }
}
