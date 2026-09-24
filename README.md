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
| **Bağlam (ABAC)** — klinik veride: tedavi ilişkisi + mesai + görevde olma | `ClinicalAccessPolicy` | **403** |

Üçüncü katman kritiktir: rol denetimi tek başına, bir hastanın *başka* bir
hastanın randevularını okumasını engellemez. Sahiplik kontrolü kimliği kaydın
sahibiyle karşılaştırır.

Dördüncü katman yalnızca klinik veride devreye girer: sahiplik "bu kayıt kimin?"
sorusunu yanıtlar, bağlam katmanı ise "bu kişi **şu anda** bu veriye erişmeli mi?"
sorusunu sorar.

### Yetki matrisi

> Bu tablo elle yazılmış bir belge değildir: `AuthorizationMatrixTest`, 28 uç
> noktanın her birini dört aktörle (kimliksiz · hasta · doktor · yönetici)
> deneyerek **112 kontrol** yapar ve tablonun koddaki karşılığını doğrular.
> Test, yetkilendirme sonucunu iş sonucundan ayırır: reddedilmesi beklenen
> hücrede kodun tam olarak 401/403 olması, izin verilmesi beklenen hücrede ise
> 401/403 **olmaması** aranır (200/400/404 fark etmez — yetki katmanı geçilmiştir).

| Uç nokta | PATIENT | DOCTOR | ADMIN |
|---|:--:|:--:|:--:|
| `POST /api/users/register`, `/login` | açık | açık | açık |
| `GET /api/departments`, `/api/doctors` | ✅ | ✅ | ✅ |
| `GET /api/appointments/available-slots` | ✅ | ✅ | ✅ |
| `GET /api/appointments/recommendations` | ✅ kendisi için | ❌ | ❌ |
| `POST /api/appointments` | ✅ kendi adına | ❌ | ❌ |
| `GET /api/appointments/patient/{id}` | ✅ kendi geçmişi | ❌ | ❌ |
| `GET /api/appointments/doctor/{id}` | ❌ | ✅ kendi listesi | ❌ |
| `PATCH /api/appointments/{id}/cancel` | ✅ kendi randevusu * | ✅ kendi randevusu | ✅ tümü |
| `PATCH /api/appointments/{id}/note` | ❌ | ✅ kendi randevusu | ❌ |
| `PATCH /api/appointments/{id}/confirm` · `/complete` | ❌ | ✅ kendi randevusu | ❌ |
| `GET/POST/DELETE /api/doctors/{id}/leaves` | ❌ | ✅ kendi kaydı | ❌ |
| `GET /api/patient-profiles/by-user/{id}` | ✅ kendi profili | ❌ | ❌ |
| `GET /api/lab-results/patient/{id}` | ✅ kendi sonuçları | ✅ **bağlam koşullu** | ❌ |
| `POST /api/lab-results` | ❌ | ✅ **bağlam koşullu** | ❌ |
| `GET /api/users` · `GET /api/appointments` (tümü) | ❌ | ❌ | ✅ |
| `GET /api/admin/stats` · `/users` · `/appointments` | ❌ | ❌ | ✅ |
| `PATCH /api/users/me/password` | ✅ kendi şifresi | ✅ | ✅ |
| `PATCH /api/admin/users/{id}/role` | ❌ | ❌ | ✅ |
| `POST /api/admin/doctors` · `/departments` | ❌ | ❌ | ✅ |
| `GET /api/admin/audit-logs` | ❌ | ❌ | ✅ |

\* Hasta iptali için R10 (iptal penceresi) kuralı geçerlidir.

### Kimlik doğrulama güvenliği

| Önlem | Değer | Nerede |
|---|---|---|
| Şifre politikası (kayıtta) | en az 8 karakter, harf + rakam, yaygın şifre listesi | `rules/PasswordPolicy` |
| Kaba kuvvet koruması | 5 ardışık hatalı denemede 15 dakika kilit | `service/LoginAttemptService` |
| Kullanıcı sayımına karşı | kayıtlı olmayan e-posta da "e-posta veya şifre hatalı" döner | `UserController` |
| Şifre saklama | BCrypt | `SecurityConfig` |
| Şifre değiştirme | mevcut şifre teyidi + politika + eski şifreyle aynı olamaz | `UserController` |
| Token geçersizleştirme | şifre değişince önceki tüm token'lar reddedilir | `JwtAuthenticationFilter` |

Kilit sayacı **veritabanında** tutulur; sunucu yeniden başlasa bile kilit sürer
(bellekte tutulsaydı yeniden başlatma saldırgan için bir kaçış yolu olurdu).
Kilit kalıcı değildir: süresi dolunca hesap kendiliğinden açılır — kalıcı kilit,
saldırganın başkasının hesabını bilerek kilitlemesine yol açardı.

### Durumsuz kimlik doğrulamanın bedeli ve telafisi

JWT durumsuzdur: üretildikten sonra sunucuda saklanmaz, bu yüzden **tek tek
iptal edilemez**. Çalınmış bir token, süresi dolana kadar geçerli kalır.

Sistemde iki telafi vardır:

1. Token ömrü sınırlıdır (24 saat)
2. Kullanıcı şifresini değiştirdiğinde `users.password_changed_at` damgası atılır.
   `JwtAuthenticationFilter`, token'ın `iat` (üretim anı) değerini bu damgayla
   karşılaştırır ve **damgadan önce üretilmiş tüm token'ları reddeder**.

Böylece şifre değiştirmek, o hesapla açılmış bütün oturumları düşürür. Şifre
değiştirirken mevcut şifrenin sorulması da bunun parçasıdır: sorulmasaydı,
çalınmış bir token hesabın kalıcı olarak ele geçirilmesine yeterdi.

### Tek yol ilkesi

Doktor ve poliklinik tanımlama yalnızca `/api/admin/**` altından yapılabilir.
Aynı işi yapan eski `POST /api/doctors` ve `POST /api/departments` uçları
kaldırıldı: girdi doğrulaması yapmıyor ve **denetim kaydı bırakmıyorlardı**.
Bir yönetim işleminin denetimsiz ikinci bir yolu varsa, denetim kaydı eksiksiz
sayılamaz.

Bu kusur yetki matrisi testi sırasında görüldü: iki uç, bozuk gövdeyle
çağrıldığında 400 yerine 500 döndürüyordu.

### Bağlam farkındalı klinik erişim (ABAC)

Rol tabanlı yetkilendirme *"DOCTOR rolü tahlil sonucu okuyabilir"* der. Sağlık
verisinde bu **yeterli değildir**: bu kural, hastanedeki her doktora her hastanın
verisini açar. Projenin ilk hâlinde tam olarak böyleydi — kodun yorumunda da
açıkça yazıyordu: *"Doktor rolü ise herhangi bir hastanın sonuçlarını görebilir"*.

Rolün üstüne üç bağlamsal koşul eklendi (`ClinicalAccessPolicy`):

| Koşul | Ne sorar | Neden |
|---|---|---|
| **K1 Tedavi ilişkisi** | Doktorun bu hastayla, tanımlı pencerede iptal edilmemiş randevusu var mı? | Hasta gizliliği: veri yalnızca tedaviyi üstlenen doktora açıktır |
| **K2 Mesai penceresi** | Erişim anı mesai saatleri içinde mi? | Mesai dışı erişim, normal klinik akışın parçası değildir |
| **K3 Görevde olma** | Doktor o gün izinli mi? | İzindeki hesabın klinik veriye erişmesi beklenmez |

Üçü de sağlanırsa erişim verilir; aksi hâlde **403** ve anlaşılır bir gerekçe döner.
Tedavi ilişkisi zaman pencereli tanımlanmıştır (varsayılan: geçmiş 90 gün, gelecek
30 gün) — üç yıl önce bir kez muayene eden doktorun süresiz erişimi olmamalıdır.

#### Acil erişim ("kırıl-camı")

Katı bir politika gerçek bir acil durumda hastaya zarar verebilir: mesai dışında
gelen hastanın tahlilini doktorun görememesi kabul edilebilir değildir. Bu yüzden
`X-Acil-Erisim` başlığında **gerekçe bildirilerek** politika aşılabilir.

Erişim engellenmez, **hesap sorulur**: her aşım denetim kaydına ayrı bir işlem
türü olarak (`CLINICAL_ACCESS_EMERGENCY`), gerekçesi ve hangi koşulun
sağlanmadığıyla birlikte yazılır ve yönetici panelinde görünür. Gerekçe eşik
uzunluğun altındaysa aşım kabul edilmez.

Bu tasarım *"her şeyi kilitle"* değil, *"erişimi bağlama göre daralt, istisnaları
hesap verebilir kıl"* ilkesine dayanır.

#### Erişim günlüğü

Klinik veri erişiminde **hem verilen hem reddedilen** her istek kaydedilir.
Sağlık verisinde "kim neye eriştiğini" sonradan gösterebilmek, erişimi kısıtlamak
kadar önemlidir. Ölçülen örnek kayıtlar:

```
14:19:05  ACİL ERİŞİM — politika aşıldı    ilişki=yok mesai=içinde görevde=evet [ACİL ERİŞİM]
14:19:05  Klinik veri erişimi reddedildi   ilişki=yok mesai=içinde görevde=evet · Bu hasta size atanmamış
14:19:05  Klinik veriye erişildi           ilişki=var mesai=içinde görevde=evet · Erişim verildi
```

#### Ayarlanabilirlik

```properties
mhrs.klinik-erisim.mesai-baslangic=08:00
mhrs.klinik-erisim.mesai-bitis=18:00
mhrs.klinik-erisim.iliski-gecmis-gun=90
mhrs.klinik-erisim.iliski-gelecek-gun=30
mhrs.klinik-erisim.acil-erisim-acik=true
mhrs.klinik-erisim.acil-gerekce-min-uzunluk=10
```

### Denetim kaydı (audit log)

Yetkilendirmeyi ilgilendiren işlemler iz bırakır: **kim, ne zaman, neyi, hangi
adresten**. `GET /api/admin/audit-logs` yalnızca ADMIN rolüne açıktır ve
yalnızca okunur — kayıtları silen ya da değiştiren bir uç bilinçli olarak
tanımlanmamıştır; aksi hâlde izin kendisi değiştirilebilir olur ve denetim
değerini yitirirdi.

İzlenen olaylar: başarılı/başarısız giriş, hesap kilitlenmesi, yeni kayıt,
**rol değişikliği**, doktor ve poliklinik tanımlama, randevu iptali ve durum
değişikliği.

Bu, rol tabanlı yetkilendirmenin tamamlayıcı yarısıdır: "kim neyi yapabilir"
sorusunu erişim matrisi yanıtlar, "kim neyi yaptı" sorusunu ise yalnızca iz kaydı.

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

#### Ölçülmüş sonuç: eşzamanlılık deneyi

Üçüncü katmanın gerekliliği mimari bir varsayım değil, ölçülmüş bir bulgudur.
`ConcurrentBookingExperimentTest`, **aynı doktorun aynı saatine 20 farklı hastadan
eşzamanlı talep** gönderir (tüm istekler bir `CountDownLatch` ile aynı anda
serbest bırakılır):

| Deney | Veritabanı garantisi | Kabul edilen | Kaydedilen | Mükerrer |
|---|:--:|:--:|:--:|:--:|
| **A** | açık (kısmi unique index) | 1 | **1** | 0 |
| **B** | kapalı (yalnızca uygulama kontrolü) | 10 | **10** | **9** |

**Yorum:** Uygulama katmanındaki "önce kontrol et, sonra kaydet" mantığı, 20
eşzamanlı istekte 10 talebin kontrolü birlikte geçmesine engel olamadı ve aynı
saate 10 randevu kaydedildi. Aynı senaryoda kısmi unique index etkinken yalnızca
1 kayıt oluştu, 19 talep reddedildi.

Deney B'nin sonucu olasılıksaldır (yarış penceresi her çalıştırmada aynı genişlikte
yakalanmaz); bu yüzden testte katı bir eşitlik değil, ölçüm raporlanır. Deney A ise
deterministiktir ve kesin olarak doğrulanır.

Deneyi çalıştırmak için:

```bash
./mvnw test -Dtest=ConcurrentBookingExperimentTest
```

> Deney B ölçüm yapabilmek için index'i geçici olarak düşürür ve bitince geri
> oluşturur. Test yarıda kesilirse index eksik kalabilir; bu yüzden hem kurulum
> hem toparlama adımında index'in varlığı yeniden sağlanır.

---

## Randevu optimizasyon motoru

Kural katmanı bir adayın **geçerli** olup olmadığını söyler; optimizasyon motoru
geçerli adaylar arasından hangisinin **daha iyi** olduğunu belirler.

> *"Hasta için mevcut alternatifler arasından en uygun randevu nasıl seçilir?"*

Bir poliklinikte 30 günlük pencerede tipik olarak **2000'den fazla geçerli aday**
bulunur. Motor hepsini puanlar ve en iyilerini döndürür:

```
Maliyet = w₁·Bekleme + w₂·Doluluk + w₃·Varyans + w₄·Risk        (düşük = iyi)
```

| Bileşen | Ne ölçer | Neden |
|---|---|---|
| **Bekleme** | Hasta kaç gün bekleyecek (0–30 normalize) | Erken randevu hasta için daha iyidir |
| **Doluluk** | Doktorun o günkü doluluğunun hedeften sapması | Kapasite kullanılmalı ama taşırılmamalı |
| **Varyans** | Adayın beklemesinin sistem ortalamasından sapması | Bekleme dağılımı daraltılır: kimse 1 gün, başkası 29 gün beklemesin |
| **Risk** | P(iptal) × o günün doluluğu | Beklenen kapasite kaybı planlama maliyetine dahil edilir |

### Neden %100 doluluk hedef değil?

Doluluk maliyeti **asimetriktir**:

```
u ≤ hedef →  (hedef − u) / hedef                      ... atıl kapasite, hafif ceza
u > hedef →  ceza × (u − hedef) / (1 − hedef)         ... aşırı yükleme, ağır ceza
```

Varsayılan hedef **%85**'tir. Tamamen dolu bir gün mola, gecikme, acil hasta ve
beklenmeyen durumlar için pay bırakmaz; tek bir gecikme zincirleme olarak günün
tamamını kaydırır. Ölçülen değerler:

| Doluluk | Maliyet |
|---|---|
| %85 (hedef) | **0.00** ← tercih edilen |
| ~%5 (neredeyse boş) | 0.94 |
| ~%98 (aşırı yüklü) | 1.73 |

Yani sistem kapasiteyi doldurmaya çalışır, fakat son slotu doldurmaktan kaçınır.

### İptal riski modeli

`P(iptal) = σ(w·x + b)` — lojistik regresyon, **sistemdeki geçmiş randevular
üzerinde eğitilir** (toplu gradyan inişi, L2 düzenlileştirme).

Öznitelikler: randevuya kalan gün · hastanın geçmiş iptal oranı (Laplace
düzeltmeli) · randevu saati · randevu geçmişi uzunluğu.

Yeterli örnek yoksa (< 20) eğitim yapılmaz; literatürdeki yönlere uygun soğuk
başlangıç ağırlıkları kullanılır ve bu durum yanıtta **açıkça raporlanır** —
model "eğitilmiş gibi" davranmaz.

**Risk hiçbir adayı elemez.** Yalnızca sıralamayı etkiler: iptal olasılığı
randevuya kalan süreye bağlı olduğundan, riskli hasta için motor kendiliğinden
daha yakın tarihe yönelir (riski azaltan yön); doluluk çarpanı ise en çok talep
gören slotların yüksek riskli randevularla işgal edilmesini sınırlar.

### Çeşitlilik kısıtı

En düşük maliyetli adaylar neredeyse her zaman aynı günün ardışık saatleridir
(14:00, 14:15, 14:30…). Bu liste teknik olarak "en iyi üç" olsa da kullanıcıya
gerçek bir seçim sunmaz; bu yüzden her (doktor, gün) çiftinden en fazla bir aday
önerilir.

### Ölçülmüş sonuç: senaryo deneyi

Aynı poliklinikte üç doktor, yarın için farklı dolulukta kurgulanır:

| Doktor | Yarınki doluluk | Motorun seçimi |
|---|---|---|
| A | ~%95 (aşırı yüklü) | — |
| **B** | **~%85 (hedef)** | ✅ **seçildi** |
| C | %0 (boş) | — |

Motor, bir gün daha beklemeyi göze alarak hedef doluluktaki günü seçti; ne boş
günü (israf) ne de tıka basa dolu günü (kırılgan plan) tercih etti.

```bash
./mvnw test -Dtest=OptimizerScenarioTest
```

### Ayarlanabilirlik

Tüm katsayılar `application.properties` üzerinden değiştirilebilir; tez kapsamında
duyarlılık analizi için:

```properties
mhrs.optimizasyon.bekleme-agirligi=0.40
mhrs.optimizasyon.doluluk-agirligi=0.25
mhrs.optimizasyon.varyans-agirligi=0.15
mhrs.optimizasyon.risk-agirligi=0.20
mhrs.optimizasyon.hedef-doluluk=0.85
mhrs.optimizasyon.asiri-yuk-cezasi=2.0
```

---

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
| `PasswordPolicyTest` | 13 | Kabul/ret edilen şifre biçimleri |
| `LoginAttemptServiceTest` | 7 | Kilitlenme eşiği ve kilidin süreyle açılması |
| `PasswordChangeTest` | 9 | Şifre değiştirme kuralları ve token geçersizleştirme |
| `ConcurrentBookingExperimentTest` | 2 | Eşzamanlı talepte veritabanı garantisinin ölçümü |
| `AuthorizationMatrixTest` | 112 | 28 uç nokta × 4 aktör: tam yetki matrisi |
| `UtilizationCostTest` | 8 | Asimetrik doluluk maliyeti; %100 doluluğun hedef olmadığı |
| `CancellationRiskModelTest` | 7 | Lojistik regresyonun davranışı ve soğuk başlangıç |
| `OptimizerScenarioTest` | 4 | Motorun hedef dolulukta doktoru seçmesi, çeşitlilik, riskli hasta |
| `ClinicalAccessPolicyTest` | 14 | Bağlam koşulları, sınır değerler ve acil erişim davranışı |
| `MerkeziRandevuSistemiApplicationTests` | 1 | Uygulama bağlamı |

Zamana bağlı testler sabit bir referans an kullanır; sonuçlar günün saatinden
bağımsızdır.

---

## Savunma demo senaryosu

`docs/savunma-senaryosu.md`: jüri önünde adım adım ne gösterileceğini, her adımın
hangi tez iddiasını kanıtladığını, olası soruların cevaplarını ve bir şey ters
giderse uygulanacak planı içerir.

## Tez figürleri

`docs/figurler/` klasöründe tez metnine eklenebilecek görseller bulunur: veritabanı
ER diyagramı, katmanlı mimari, istek akışı (401/403/200 kararı), randevu durum
makinesi, kural denetimi akışı ve sekiz ekran görüntüsü. Diyagramların Mermaid
kaynakları da aynı klasördedir; yeniden üretme adımları `docs/figurler/README.md`
içinde anlatılmıştır.

## Proje yapısı

```
config/      SecurityConfig (yetki kuralları), WebConfig, DataLoader
security/    JwtUtil, JwtAuthenticationFilter, AuthenticatedUser
rules/       AppointmentScheduleRules, AppointmentStatusRules, PasswordPolicy
optimization/ AppointmentOptimizer, CancellationRiskModel, OptimizationWeights, SlotScore
policy/      ClinicalAccessPolicy, ClinicalAccessProperties
controller/  REST uçları — rol ve sahiplik kontrolleri burada
service/     İş kuralları
repository/  JPA sorguları
model/       Entity sınıfları
dto/         İstek/yanıt taşıyıcıları
```
