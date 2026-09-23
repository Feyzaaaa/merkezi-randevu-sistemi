package com.hastane.merkezi_randevu_sistemi.service;

import com.hastane.merkezi_randevu_sistemi.dto.DoctorCreateRequest;
import com.hastane.merkezi_randevu_sistemi.model.*;
import com.hastane.merkezi_randevu_sistemi.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ROL YÖNETİMİ İŞ KURALLARININ TESTİ
 *
 * Rol değiştirmek, kullanıcının erişebildiği tüm uç noktaları anında değiştiren
 * en kritik yönetici işlemidir. Bu testler, sistemi tutarsız ya da erişilemez
 * bırakacak senaryoların reddedildiğini belgeler.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private AppointmentRepository appointmentRepository;

    @InjectMocks private AdminService adminService;

    private static final Long YONETICI_ID = 1L;
    private static final Long HEDEF_ID = 2L;

    private User kullanici(Long id, Role rol) {
        User user = new User();
        user.setId(id);
        user.setEmail("kullanici" + id + "@test.com");
        user.setFirstName("Ad");
        user.setLastName("Soyad");
        user.setRole(rol);
        return user;
    }

    private String rolDegistirVeHatayiAl(Long hedefId, String yeniRol) {
        return assertThrows(IllegalArgumentException.class,
                () -> adminService.updateUserRole(hedefId, yeniRol, YONETICI_ID)).getMessage();
    }

    @Test
    @DisplayName("Yönetici kendi rolünü değiştiremez (sisteme erişimi kaybetme riski)")
    void yoneticiKendiRolunuDegistiremez() {
        String hata = rolDegistirVeHatayiAl(YONETICI_ID, "PATIENT");
        assertTrue(hata.contains("Kendi rolünüzü"), "Beklenmeyen mesaj: " + hata);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tanımsız bir rol değeri reddedilir")
    void gecersizRolReddedilir() {
        String hata = rolDegistirVeHatayiAl(HEDEF_ID, "SUPERUSER");
        assertTrue(hata.contains("Geçersiz rol"), "Beklenmeyen mesaj: " + hata);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Doktor kaydı olmayan kullanıcı DOCTOR rolüne yükseltilemez")
    void doktorKaydiOlmayanDoctorYapilamaz() {
        when(userRepository.findById(HEDEF_ID)).thenReturn(Optional.of(kullanici(HEDEF_ID, Role.PATIENT)));
        when(doctorRepository.existsByUserId(HEDEF_ID)).thenReturn(false);

        String hata = rolDegistirVeHatayiAl(HEDEF_ID, "DOCTOR");
        assertTrue(hata.contains("doktor kaydı yok"), "Beklenmeyen mesaj: " + hata);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Doktor kaydına bağlı kullanıcının rolü düşürülemez (randevuları sahipsiz kalır)")
    void doktorKaydinaBagliKullaniciDusurulemez() {
        when(userRepository.findById(HEDEF_ID)).thenReturn(Optional.of(kullanici(HEDEF_ID, Role.DOCTOR)));
        when(doctorRepository.existsByUserId(HEDEF_ID)).thenReturn(true);

        String hata = rolDegistirVeHatayiAl(HEDEF_ID, "PATIENT");
        assertTrue(hata.contains("sahipsiz"), "Beklenmeyen mesaj: " + hata);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sistemdeki son yönetici rolünden düşürülemez")
    void sonYoneticiDusurulemez() {
        when(userRepository.findById(HEDEF_ID)).thenReturn(Optional.of(kullanici(HEDEF_ID, Role.ADMIN)));
        when(doctorRepository.existsByUserId(HEDEF_ID)).thenReturn(false);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        String hata = rolDegistirVeHatayiAl(HEDEF_ID, "PATIENT");
        assertTrue(hata.contains("en az bir yönetici"), "Beklenmeyen mesaj: " + hata);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Geçerli yükseltme uygulanır: hasta -> yönetici")
    void gecerliRolYukseltmesiUygulanir() {
        User hedef = kullanici(HEDEF_ID, Role.PATIENT);
        when(userRepository.findById(HEDEF_ID)).thenReturn(Optional.of(hedef));
        when(doctorRepository.existsByUserId(HEDEF_ID)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var sonuc = adminService.updateUserRole(HEDEF_ID, "ADMIN", YONETICI_ID);

        assertEquals(Role.ADMIN, sonuc.getRole());
        verify(userRepository).save(hedef);
    }

    @Test
    @DisplayName("Aynı isimde ikinci poliklinik tanımlanamaz")
    void ayniIsimdePoliklinikEklenemez() {
        when(departmentRepository.existsByNameIgnoreCase("Kardiyoloji")).thenReturn(true);

        String hata = assertThrows(IllegalArgumentException.class,
                () -> adminService.createDepartment("Kardiyoloji")).getMessage();

        assertTrue(hata.contains("zaten var"), "Beklenmeyen mesaj: " + hata);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Poliklinik adındaki baştaki/sondaki boşluklar temizlenir")
    void poliklinikAdiTemizlenir() {
        when(departmentRepository.existsByNameIgnoreCase("Nöroloji")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        Department sonuc = adminService.createDepartment("  Nöroloji  ");

        assertEquals("Nöroloji", sonuc.getName());
    }

    @Test
    @DisplayName("Doktor tanımlandığında kullanıcının rolü de DOCTOR'a yükseltilir")
    void doktorTanimlanincaRolYukseltilir() {
        User hedef = kullanici(HEDEF_ID, Role.PATIENT);
        Department poliklinik = new Department("Dahiliye");
        poliklinik.setId(5L);

        when(userRepository.findById(HEDEF_ID)).thenReturn(Optional.of(hedef));
        when(doctorRepository.existsByUserId(HEDEF_ID)).thenReturn(false);
        when(departmentRepository.findById(5L)).thenReturn(Optional.of(poliklinik));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorCreateRequest istek = new DoctorCreateRequest();
        istek.setUserId(HEDEF_ID);
        istek.setDepartmentId(5L);
        istek.setTitle("Uzm. Dr.");

        Doctor sonuc = adminService.createDoctor(istek);

        ArgumentCaptor<User> kaydedilen = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(kaydedilen.capture());
        assertEquals(Role.DOCTOR, kaydedilen.getValue().getRole(), "Kullanıcının rolü DOCTOR'a yükseltilmeliydi");
        assertEquals("Uzm. Dr.", sonuc.getTitle());
        assertEquals(poliklinik, sonuc.getDepartment());
    }

    @Test
    @DisplayName("Zaten doktor olan kullanıcı ikinci kez tanımlanamaz")
    void ayniKullaniciIkinciKezDoktorYapilamaz() {
        when(userRepository.findById(HEDEF_ID)).thenReturn(Optional.of(kullanici(HEDEF_ID, Role.DOCTOR)));
        when(doctorRepository.existsByUserId(HEDEF_ID)).thenReturn(true);

        DoctorCreateRequest istek = new DoctorCreateRequest();
        istek.setUserId(HEDEF_ID);
        istek.setDepartmentId(5L);

        String hata = assertThrows(IllegalArgumentException.class,
                () -> adminService.createDoctor(istek)).getMessage();

        assertTrue(hata.contains("zaten doktor"), "Beklenmeyen mesaj: " + hata);
        verify(doctorRepository, never()).save(any());
    }
}
