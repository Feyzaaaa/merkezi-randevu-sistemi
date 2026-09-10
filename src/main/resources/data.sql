-- 1. Tabloları temizleyelim (Yabancı anahtar hatalarını önlemek için sıra önemli)
TRUNCATE TABLE appointments CASCADE;
TRUNCATE TABLE doctors CASCADE;
TRUNCATE TABLE departments CASCADE;
TRUNCATE TABLE users CASCADE;

-- 2. Poliklinikleri ekle
INSERT INTO departments (name) VALUES ('Dahiliye');
INSERT INTO departments (name) VALUES ('Göz Hastalıkları');

-- 3. Kullanıcıları ekle
INSERT INTO users (email, password, first_name, last_name, role) 
VALUES ('ahmet@hastane.com', '123456', 'Ahmet', 'Yılmaz', 'DOCTOR');
INSERT INTO users (email, password, first_name, last_name, role) 
VALUES ('ayse@hastane.com', '123456', 'Ayşe', 'Kaya', 'DOCTOR');

-- 4. Doktorları eşleştir (ID'ye güvenmek yerine isme göre eşleştiriyoruz)
INSERT INTO doctors (user_id, department_id, title) 
VALUES (
    (SELECT id FROM users WHERE email = 'ahmet@hastane.com'), 
    (SELECT id FROM departments WHERE name = 'Dahiliye'), 
    'Uzm. Dr.'
);

INSERT INTO doctors (user_id, department_id, title) 
VALUES (
    (SELECT id FROM users WHERE email = 'ayse@hastane.com'), 
    (SELECT id FROM departments WHERE name = 'Göz Hastalıkları'), 
    'Prof. Dr.'
);