package com.hastane.merkezi_randevu_sistemi.controller;
import com.hastane.merkezi_randevu_sistemi.dto.AuthResponse;
import com.hastane.merkezi_randevu_sistemi.dto.LoginRequest;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.model.Role; // Role enum'ını import etmeyi unutma!
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.security.JwtUtil;
import com.hastane.merkezi_randevu_sistemi.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        // 1. Email kontrolü
        if (userRepository.findByEmailIgnoreCase(user.getEmail().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Bu e-posta adresi zaten kullanımda!");
        }

        // 2. DOĞRU KULLANIM: String yerine direkt Enum değerini atıyoruz
        user.setRole(Role.PATIENT);

        // 2b. Şifreyi düz metin değil, BCrypt hash'i olarak sakla
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 3. Kaydet
        User savedUser = userService.saveUser(user);
        System.out.println("Sisteme yeni bir hasta kaydoldu: " + savedUser.getEmail());
        
        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body("E-posta veya şifre boş olamaz!");
        }

        String cleanEmailInput = loginRequest.getEmail().trim();
        String passwordInput = loginRequest.getPassword();

        System.out.println("--- GİRİŞ SİSTEMİ ÇALIŞTI ---");
        
        return userRepository.findByEmailIgnoreCase(cleanEmailInput)
                .map(user -> {
                    if (passwordEncoder.matches(passwordInput, user.getPassword())) {
                        System.out.println("Giriş Başarılı: " + user.getFirstName() + " Role: " + user.getRole());
                        // Rol tabanlı yetkilendirme için: kimliği ve rolü taşıyan imzalı token üretilir
                        String token = jwtUtil.generateToken(user);
                        AuthResponse authResponse = new AuthResponse(
                                user.getId(), user.getEmail(), user.getFirstName(),
                                user.getLastName(), user.getRole(), token);
                        return ResponseEntity.ok(authResponse);
                    } else {
                        return ResponseEntity.status(401).body("Hatalı şifre!");
                    }
                })
                .orElseGet(() -> ResponseEntity.status(404).body("Kullanıcı bulunamadı!"));
    }
}