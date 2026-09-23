package com.hastane.merkezi_randevu_sistemi.service;
import com.hastane.merkezi_randevu_sistemi.model.User;
import com.hastane.merkezi_randevu_sistemi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // Tüm kullanıcıları getir
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Yeni kullanıcı kaydet
    public User saveUser(User user) {
        return userRepository.save(user);
    }
}