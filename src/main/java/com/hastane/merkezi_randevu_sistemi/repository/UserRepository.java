package com.hastane.merkezi_randevu_sistemi.repository;
import com.hastane.merkezi_randevu_sistemi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Bu metod e-postayı büyük/küçük harf bakmaksızın bulur
    Optional<User> findByEmailIgnoreCase(String email);
}