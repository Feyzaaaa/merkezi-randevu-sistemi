-- EŞZAMANLI RANDEVU TALEPLERİNE KARŞI GÜVENCE (senaryo tabanlı çakışma yönetiminin veritabanı seviyesindeki garantisi)
-- Uygulama kodundaki "önce kontrol et, sonra kaydet" mantığı tek başına yeterli değildir:
-- iki eşzamanlı istek aynı anda "boş" görüp ikisi de kaydedebilir. Bu kısmi unique index'ler,
-- iptal edilmemiş (status <> 'CANCELLED') randevular arasında aynı doktor/aynı saat ya da
-- aynı hasta/aynı saat çiftinin veritabanı seviyesinde asla ikiden fazla olamayacağını garanti eder.
CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_doctor_slot_active
    ON appointments (doctor_id, appointment_date)
    WHERE status <> 'CANCELLED';

CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_patient_slot_active
    ON appointments (patient_id, appointment_date)
    WHERE status <> 'CANCELLED';

-- GİRİŞ GÜVENLİĞİ ALANLARI (kaba kuvvet koruması)
-- Hibernate'in otomatik şema güncellemesi, DOLU bir tabloya varsayılan değeri olmayan
-- NOT NULL sütun ekleyemez (PostgreSQL bunu reddeder ve sütun sessizce oluşmaz).
-- Bu yüzden geçişi burada açıkça tanımlıyoruz; IF NOT EXISTS sayesinde her açılışta
-- güvenle tekrar çalışır.
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until timestamp;

-- Şifre değişikliği damgası: bu andan ÖNCE üretilmiş JWT'ler geçersiz sayılır
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at timestamp;

-- Randevunun alındığı an: bekleme süresi (appointment_date - created_at) ve
-- iptal riski modelinin öznitelikleri bu alandan hesaplanır.
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS created_at timestamp;
