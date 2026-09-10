package com.hastane.merkezi_randevu_sistemi.controller;
import com.hastane.merkezi_randevu_sistemi.dto.LoginRequest;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.model.Role; // Role enum'ını import etmeyi unutma!
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import com.hastane.merkezi_randevu_sistemi.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
                    if (user.getPassword().equals(passwordInput)) {
                        System.out.println("Giriş Başarılı: " + user.getFirstName() + " Role: " + user.getRole());
                        return ResponseEntity.ok(user);
                    } else {
                        return ResponseEntity.status(401).body("Hatalı şifre!");
                    }
                })
                .orElseGet(() -> ResponseEntity.status(404).body("Kullanıcı bulunamadı!"));
    }
}