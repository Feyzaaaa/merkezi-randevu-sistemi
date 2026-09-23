-- ÖRNEK VERİ YÜKLEME (idempotent / tekrar çalıştırılabilir)
--
-- ÖNEMLİ: Bu dosya spring.sql.init.mode=always ayarı yüzünden backend'in HER açılışında çalışır.
-- Bu yüzden burada TRUNCATE kullanılmaz: kayıt olan hastalar, alınan randevular ve yönetici
-- panelinden yapılan rol değişiklikleri her yeniden başlatmada silinirdi.
-- Bunun yerine her kayıt "yoksa ekle" mantığıyla yazılır; zaten varsa hiçbir şey yapılmaz.

-- 1. Poliklinikler (name sütunu UNIQUE)
INSERT INTO departments (name) VALUES ('Dahiliye') ON CONFLICT (name) DO NOTHING;
INSERT INTO departments (name) VALUES ('Göz Hastalıkları') ON CONFLICT (name) DO NOTHING;

-- 2. Kullanıcılar (email sütunu UNIQUE)
-- NOT: Şifreler BCrypt ile hashlenmiştir. Aşağıdaki hash, düz metin "123456" şifresine karşılık gelir.
INSERT INTO users (email, password, first_name, last_name, role)
VALUES ('ahmet@hastane.com', '$2a$10$BBy5Xz5a3NzVwAm2JmFjwutr8IgFt9mlj/tyS8ovwVIpY94W6t2/C', 'Ahmet', 'Yılmaz', 'DOCTOR')
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email, password, first_name, last_name, role)
VALUES ('ayse@hastane.com', '$2a$10$BBy5Xz5a3NzVwAm2JmFjwutr8IgFt9mlj/tyS8ovwVIpY94W6t2/C', 'Ayşe', 'Kaya', 'DOCTOR')
ON CONFLICT (email) DO NOTHING;

-- Yönetici (ADMIN) hesabı: rol tabanlı yetkilendirmenin üçüncü rolü.
-- Sisteme doktor/poliklinik tanımlar, rolleri yönetir ve tüm randevuları denetler.
INSERT INTO users (email, password, first_name, last_name, role)
VALUES ('admin@hastane.com', '$2a$10$BBy5Xz5a3NzVwAm2JmFjwutr8IgFt9mlj/tyS8ovwVIpY94W6t2/C', 'Sistem', 'Yöneticisi', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- 3. Doktor kayıtları (ID'ye güvenmek yerine e-posta/isme göre eşleştiriyoruz)
-- Aynı kullanıcı için ikinci bir doktor kaydı açılmasın diye NOT EXISTS ile korunuyor.
INSERT INTO doctors (user_id, department_id, title)
SELECT u.id, d.id, 'Uzm. Dr.'
FROM users u, departments d
WHERE u.email = 'ahmet@hastane.com'
  AND d.name = 'Dahiliye'
  AND NOT EXISTS (SELECT 1 FROM doctors x WHERE x.user_id = u.id);

INSERT INTO doctors (user_id, department_id, title)
SELECT u.id, d.id, 'Prof. Dr.'
FROM users u, departments d
WHERE u.email = 'ayse@hastane.com'
  AND d.name = 'Göz Hastalıkları'
  AND NOT EXISTS (SELECT 1 FROM doctors x WHERE x.user_id = u.id);
