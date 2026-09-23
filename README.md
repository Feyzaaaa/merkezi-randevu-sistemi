# MHRS — Merkezi Hastane Randevu Sistemi (Backend)

Lisans tezi kapsamında geliştirilen randevu sisteminin sunucu tarafı.
Tezin iki ekseni: **senaryo tabanlı çakışma yönetimi** ve **rol tabanlı yetkilendirme**.

- **Teknolojiler:** Java 21, Spring Boot 4, Spring Security, JPA/Hibernate, PostgreSQL, JJWT
- **Arayüz (ayrı depo):** React + Ant Design — `mhrs-frontend`

---

## Kurulum

### 1. Veritabanı

PostgreSQL'de `mhrs_db` adında bir veritabanı oluşturun. Tablolar uygulama ilk
açılışta otomatik oluşur (`spring.jpa.hibernate.ddl-auto=update`).

### 2. Gizli ayarlar

Şifreler depoya **gönderilmez**. Şablonu kopyalayıp kendi değerlerinizi yazın:

```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
```

| Ayar | Açıklama |
|---|---|
| `spring.datasource.password` | PostgreSQL şifreniz |
| `mhrs.jwt.secret` | Token imzalama anahtarı (en az 32 karakter) |
| `spring.mail.username` / `password` | Bildirim e-postaları için Gmail hesabı ve **uygulama şifresi** |

Bu dosya `.gitignore` içindedir; `application.properties` yalnızca `${...}` yer
tutucuları taşır.

### 3. Çalıştırma

```bash
./mvnw spring-boot:run     # http://localhost:8081
./mvnw test                # test takımı
```

### Örnek hesaplar

İlk açılışta `data.sql` ile yüklenir (şifre: `123456`). Dosya
tekrar çalıştırılabilir yapıdadır — mevcut veriyi silmez, eksik kaydı ekler.

| E-posta | Rol |
|---|---|
| `admin@hastane.com` | ADMIN |
| `ahmet@hastane.com`, `ayse@hastane.com` | DOCTOR |
| (Kayıt Ol ekranından açılan hesaplar) | PATIENT |

---

## Rol tabanlı yetkilendirme

Erişim denetimi **üç katmanlıdır**:

| Katman | Nerede | Yanıt |
|---|---|---|
| **Kimlik doğrulama** — token var mı, geçerli mi? | `JwtAuthenticationFilter` | **401** |
| **Rol yetkisi** — bu rol bu uca erişebilir mi? | `SecurityConfig` | **403** |
| **Nesne sahipliği** — bu kayıt gerçekten bu kullanıcının mı? | Controller (`AuthenticatedUser`) | **403** |

Üçüncü katman kritiktir: rol denetimi tek başına, bir hastanın *başka* bir
hastanın randevularını okumasını engellemez. Sahiplik kontrolü kimliği kaydın
sahibiyle karşılaştırır.

### Yetki matrisi

| Uç nokta | PATIENT | DOCTOR | ADMIN |
|---|:--:|:--:|:--:|
| `POST /api/users/register`, `/login` | açık | açık | açık |
| `GET /api/departments`, `/api/doctors` | ✅ | ✅ | ✅ |
| `GET /api/appointments/available-slots` | ✅ | ✅ | ✅ |
| `POST /api/appointments` | ✅ kendi adına | ❌ | ❌ |
| `GET /api/appointments/patient/{id}` | ✅ kendi geçmişi | ❌ | ❌ |
| `GET /api/appointments/doctor/{id}` | ❌ | ✅ kendi listesi | ❌ |
| `PATCH /api/appointments/{id}/cancel` | ✅ kendi randevusu * | ✅ kendi randevusu | ✅ tümü |
| `PATCH /api/appointments/{id}/note` | ❌ | ✅ kendi randevusu | ❌ |
| `PATCH /api/appointments/{id}/confirm` · `/complete` | ❌ | ✅ kendi randevusu | ❌ |
| `GET/POST/DELETE /api/doctors/{id}/leaves` | ❌ | ✅ kendi kaydı | ❌ |
| `GET /api/patient-profiles/by-user/{id}` | ✅ kendi profili | ❌ | ❌ |
| `GET /api/lab-results/patient/{id}` | ✅ kendi sonuçları | ✅ | ❌ |
| `POST /api/lab-results` | ❌ | ✅ | ❌ |
| `GET /api/users` · `GET /api/appointments` (tümü) | ❌ | ❌ | ✅ |
| `POST /api/doctors` · `POST /api/departments` | ❌ | ❌ | ✅ |
| `GET /api/admin/stats` · `/users` · `/appointments` | ❌ | ❌ | ✅ |
| `PATCH /api/admin/users/{id}/role` | ❌ | ❌ | ✅ |
| `POST /api/admin/doctors` · `/departments` | ❌ | ❌ | ✅ |

\* Hasta iptali için R10 (iptal penceresi) kuralı geçerlidir.

### Rol yönetimi tutarlılık kuralları

- Yönetici **kendi rolünü** değiştiremez (sisteme erişimi kaybetme riski)
- Doktor kaydına bağlı kullanıcının rolü düşürülemez (randevuları sahipsiz kalır)
- Doktor kaydı olmayan kullanıcı `DOCTOR` yapılamaz — önce doktor tanımlanmalı
- Sistemde **en az bir yönetici** kalmalıdır

---

## Senaryo tabanlı çakışma yönetimi

Kural kataloğu: `rules/AppointmentScheduleRules.java`

| Kod | Senaryo | Uygulandığı yer |
|---|---|---|
| **R1** | Randevu tarihi boş | Saf kural |
| **R2** | Geçmiş tarih/saat (bugünün geçmiş saatleri dahil) | Saf kural |
| **R3** | 30 günden ileri tarih | Saf kural |
| **R4** | 15 dakikalık slot ızgarasına oturmayan saat | Saf kural |
| **R5** | Mesai dışı (08:00–18:00 dışında) | Saf kural |
| **R6** | Öğle arası (12:00–13:00) | Saf kural |
| **R7** | Doktorun izin/görev günü | Servis (veri gerektirir) |
| **R8** | Aynı gün aynı poliklinikten ikinci randevu | Servis (veri gerektirir) |
| **R9** | Hasta başına en fazla 5 aktif randevu | Servis (veri gerektirir) |
| **R10** | Randevuya 1 saatten az kala hasta iptali | Servis (veri gerektirir) |

Ayrıca **aynı saat çakışması** üç katmanda önlenir:

1. **Arayüz** — dolu ve kurala aykırı saatler listelenmez (kolaylık, güvenlik değil)
2. **Uygulama** — `createAppointment` kaydetmeden önce denetler
3. **Veritabanı** — `schema.sql` içindeki kısmi unique index'ler, iki isteğin
   kontrolü aynı anda geçtiği yarış durumunda (race condition) ikinci kaydı reddeder

Bu ayrım tezin temel savıdır: *istemci tarafı doğrulama bir kolaylıktır; kural
sunucuda, garanti veritabanındadır.*

### Randevu durum akışı

```
PENDING ──onayla──> CONFIRMED ──tamamla──> COMPLETED
   │                    │
   └──────iptal─────────┴──> CANCELLED
```

`COMPLETED` ve `CANCELLED` son durumlardır. Saati gelmemiş randevu tamamlanamaz;
saati geçmiş randevu onaylanamaz (tamamlanır veya iptal edilir).

---

## Testler

```bash
./mvnw test
```

| Sınıf | Test | Kapsam |
|---|:--:|---|
| `AppointmentScheduleRulesTest` | 24 | R1–R6 ve sınır durumları |
| `AppointmentServiceTest` | 12 | R7–R10, çakışma kontrolleri, slot üretimi |
| `AppointmentStatusRulesTest` | 10 | Durum makinesindeki tüm geçişler |
| `AdminServiceTest` | 10 | Rol yönetimi tutarlılık kuralları |
| `AdminEndpointAuthorizationTest` | 6 | Gerçek JWT ile 401/403/200 ayrımı |
| `MerkeziRandevuSistemiApplicationTests` | 1 | Uygulama bağlamı |

Zamana bağlı testler sabit bir referans an kullanır; sonuçlar günün saatinden
bağımsızdır.

---

## Proje yapısı

```
config/      SecurityConfig (yetki kuralları), WebConfig, DataLoader
security/    JwtUtil, JwtAuthenticationFilter, AuthenticatedUser
rules/       AppointmentScheduleRules, AppointmentStatusRules
controller/  REST uçları — rol ve sahiplik kontrolleri burada
service/     İş kuralları
repository/  JPA sorguları
model/       Entity sınıfları
dto/         İstek/yanıt taşıyıcıları
```
