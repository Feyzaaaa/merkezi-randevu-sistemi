package com.hastane.merkezi_randevu_sistemi.repository;

import com.hastane.merkezi_randevu_sistemi.model.Role;
import com.hastane.merkezi_randevu_sistemi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    // Normal arama için
    Optional<User> findByEmail(String email);
    
    // Büyük/küçük harf duyarsız arama (Hatanı çözen kahraman metot!)
    Optional<User> findByEmailIgnoreCase(String email);

    // Yönetici paneli: rol dağılımını (kaç hasta / doktor / yönetici) saymak için
    long countByRole(Role role);

    // Yönetici paneli: kullanıcı listesini kayıt sırasına göre döndürür
    List<User> findAllByOrderByIdAsc();
}
