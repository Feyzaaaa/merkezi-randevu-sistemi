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
