# Tez Savunması — Demo Senaryosu

Bu belge, jüri önünde sistemi adım adım nasıl göstereceğini anlatır. Her adımda
üç şey var: **ne yapacaksın**, **ne görülecek**, **hangi iddiayı kanıtlıyor**.

Toplam süre: yaklaşık **15–18 dakika**.

---

## Sunumdan önce hazırlık

### Çalışır durumda olmalı

| Bileşen | Kontrol |
|---|---|
| PostgreSQL | `/Library/PostgreSQL/18/bin/pg_isready -h localhost` → *accepting connections* |
| Backend (8081) | Eclipse'ten çalıştır ya da `./mvnw spring-boot:run` |
| Frontend (3000) | `npm start` |
| Terminal | 3. adım için hazır bir pencere (aşağıdaki komut yazılı, Enter'a basmaya hazır) |

### Tarayıcı sekmeleri (önceden aç)

1. `localhost:3000` — karşılama
2. Boş bir sekme (yönetici girişi için)
3. `docs/figurler/02-katmanli-mimari.png` — soru gelirse gösterilecek

**İpucu:** Tarayıcıyı gizli/incognito pencerede aç. Eski oturum kalıntısı demoyu bozar.

### Hesaplar (şifre hepsinde `123456`)

| Hesap | Rol |
|---|---|
| `testhasta@mail.com` | Hasta |
| `ahmet@hastane.com` | Doktor (Dahiliye) |
| `admin@hastane.com` | Yönetici |

### Veri durumu

`testhasta` hesabında hazır veri bulunmalı: sağlık profili, iki randevu (biri
onaylı), bir tahlil sonucu. Kayıp olursa hasta portalından 2 dakikada yeniden
oluşturulabilir.

---

## Akış

### 1. Roller sistemin kapısında ayrışır · 1 dk

**Yap:** `localhost:3000` → karşılama ekranında üç ayrı giriş butonunu göster.
**Doktor Girişi**'ne tıkla, **hasta** bilgileriyle giriş yapmayı dene.

**Görülecek:** *"Bu bilgiler bir Hasta hesabına ait. Lütfen Hasta Girişi'ni kullanın."*

**Kanıtladığı:** Doğru şifre bile tek başına yetmiyor; rol, kimlik doğrulamanın
ayrılmaz parçası olarak ele alınıyor.

> **Söylenecek cümle:** "Şifre doğru ama rol yanlış. Sistem kimliği doğruluyor,
> sonra o kimliğin bu kapıdan girip giremeyeceğine ayrıca karar veriyor."

---

### 2. Arayüz katmanı: kurallar kullanıcıya gösteriliyor · 2 dk

**Yap:** Hasta olarak gir → **Yeni Randevu Al** → poliklinik ve doktor seç →
takvimi aç.

**Görülecek:**
- Geçmiş günler ve 30 günden ileri tarihler **gri, seçilemiyor**
- Saat listesinde **12:00–13:00 arası yok** (öğle arası)
- Bugünü seçersen **geçmiş saatler listelenmiyor**

**Kanıtladığı:** Kurallar arayüze yansıyor, kullanıcı hatalı seçim yapamıyor.

> **Söylenecek cümle:** "Buraya kadarı kullanıcı kolaylığı. Şimdi bunun güvenlik
> olmadığını göstereceğim."

---

### 3. ⭐ TEZİN ÇEKİRDEĞİ: aynı isteği doğrudan API'ye gönder · 2 dk

**Yap:** Terminali aç ve şu komutu çalıştır (önceden yazılı olsun):

```bash
# Önce hasta olarak token al
TOKEN=$(curl -s -X POST http://localhost:8081/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"testhasta@mail.com","password":"123456"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# Arayüzün asla göndermeyeceği bir istek: 2020 yılına randevu
curl -X POST http://localhost:8081/api/appointments \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"patient":{"id":166},"doctor":{"id":125},
       "appointmentDate":"2020-01-15T10:00:00","complaint":"test"}'
```

**Görülecek:** `Geçmiş bir tarih/saate randevu alınamaz! [R2]`

Aynı komutla saat `03:00` (mesai dışı) ve 60 gün sonrasını da dene — hepsi
kendi kural koduyla reddediliyor.

**Kanıtladığı:** Tarayıcı devre dışı bırakılsa bile kural işliyor. **İstemci
tarafı doğrulama bir kolaylıktır, güvence değildir.**

> **Söylenecek cümle:** "Arayüzü tamamen atladım, isteği doğrudan sunucuya
> gönderdim. Kuralı uygulayan katman burası. Projenin başında bu kontrol yoktu —
> 2020 tarihine randevu kabul ediliyordu; bunu geliştirme sürecinde tespit edip
> sunucu tarafına taşıdım."

---

### 4. Senaryo tabanlı çakışma yönetimi · 2 dk

**Yap:** Hasta portalına dön, arka arkaya iki şey dene:

1. Zaten randevusu olan bir saate ikinci randevu al
2. Aynı gün, **aynı poliklinikten** başka bir saate randevu al

**Görülecek:**
1. *"Bu saat diliminde zaten mevcut bir randevunuz var…"*
2. *"Aynı gün aynı poliklinikten ikinci bir randevu alamazsınız! [R8]"*

**Kanıtladığı:** Çakışma yalnızca "aynı saat" değil; senaryo bazlı tanımlanmış
on kural var (R1–R10).

> **Soru gelirse:** "Aynı anda iki kişi aynı saate tıklarsa?" → Cevap 5. adımda.

---

### 5. Yarış durumu ve veritabanı garantisi · 1 dk

**Yap:** Şekil 5'i (`docs/figurler/05-cakisma-kurallari.png`) aç.

**Anlat:** Uygulama katmanındaki kontrol "önce bak, sonra kaydet" mantığıdır ve
tek başına yeterli değildir: iki eşzamanlı istek aynı anda "boş" görüp ikisi de
kaydedebilir. Bu yüzden `schema.sql` içinde **kısmi unique index** var —
iptal edilmemiş randevular arasında aynı doktor/saat çifti veritabanı seviyesinde
imkânsız.

**Kanıtladığı:** Üç katmanlı savunma: arayüz (kolaylık) → uygulama (kural) →
veritabanı (garanti).

---

### 5b. ⭐ Yarış durumunu ölç, anlatma · 1 dk

**Yap:** Terminalde:

```bash
./mvnw test -Dtest=ConcurrentBookingExperimentTest
```

**Görülecek:** İki kutu halinde ölçüm sonucu:

```
DENEY A — Veritabanı garantisi AÇIK      → 20 talep, 1 kabul, 1 kayıt
DENEY B — Veritabanı garantisi KAPALI    → 20 talep, 10 kabul, 9 MÜKERRER KAYIT
```

**Kanıtladığı:** Üçüncü katman süs değil. Aynı doktorun aynı saatine 20 eşzamanlı
talep gönderildiğinde, uygulama katmanı kontrolü tek başına 10 mükerrer kaydı
engelleyemedi; kısmi unique index etkinken yalnızca 1 kayıt oluştu.

> **Söylenecek cümle:** "Bu bir varsayım değil, ölçüm. Index'i kapatıp aynı deneyi
> tekrarladım ve 9 mükerrer randevu oluştu."

---

### 5c. ⭐ Optimizasyon motoru: "en uygun randevu" nasıl seçilir · 2 dk

**Yap:** Hasta portalında **Yeni Randevu Al** → bir poliklinik seç.

**Görülecek:** "Sizin İçin Önerilenler" kartı açılır; başlıkta kaç adayın
değerlendirildiği yazar (tipik olarak 1000–3000). Her öneride gerekçe vardır:
*"yarın · doktorun günü dengeli dolulukta · iptal olasılığı %24"*

**Anlat:** Kural katmanı bir adayın GEÇERLİ olup olmadığını söyler; bu motor
geçerli adaylar arasından hangisinin DAHA İYİ olduğunu belirler. Dört ölçüt
ağırlıklı bir maliyet fonksiyonunda birleşir: bekleme süresi, doktor doluluğu,
bekleme varyansı ve iptal riski.

**Sonra:** Terminalde ölçümü göster:

```bash
./mvnw test -Dtest=OptimizerScenarioTest
```

Üç doktor, yarın için sırasıyla ~%95, ~%85 ve %0 dolulukta kurgulanır. Motor
**%85'teki doktoru seçer** — ne boş günü ne tıka basa dolu günü.

> **Söylenecek cümle:** "Burada anahtar nokta şu: %100 doluluk iyi bir hedef
> değil. Dolu bir gün mola, gecikme ve acil hasta için pay bırakmaz; tek bir
> gecikme tüm günü kaydırır. Maliyet fonksiyonu bu yüzden asimetrik — atıl
> kapasite israf, aşırı yükleme ise risk ve daha ağır cezalandırılıyor."

> **Soru gelirse — "İptal riski yüksek hastaya randevu vermiyor musunuz?"**
> Veriyoruz. Risk hiçbir adayı elemez, yalnızca sıralamayı etkiler. Üstelik
> iptal olasılığı randevuya kalan süreye bağlı olduğu için motor riskli hastayı
> kendiliğinden daha yakın tarihe yönlendirir — yani riski azaltan yöne. Testte
> de bunu doğruladık: iptal geçmişi olan hastaya yine üç öneri üretiliyor.

---

### 6. Doktor portalı: izin günü ve durum akışı · 2 dk

**Yap:** Çıkış yap, doktor olarak gir.

1. **İzin / Görev Günlerim** → yarının tarihini ekle
2. Başka sekmede hasta olarak o doktoru ve o günü seç
3. Doktor portalına dön → bir randevuda **Onayla**, sonra **Muayene Bitti**

**Görülecek:**
1. İzin günü turuncu etiket olarak eklenir
2. Hasta tarafında o gün için **hiç saat listelenmez**
3. Durum `Onay Bekliyor` → `Onaylandı`; saati gelmemiş randevuda
   *"Randevu saati henüz gelmedi; muayene tamamlandı olarak işaretlenemez!"*

**Kanıtladığı:** Aynı kural kümesi hem saat listesini üretiyor hem kaydı
doğruluyor (tek kaynak). Durum makinesi geçersiz geçişleri reddediyor.

---

### 7. Yetkilendirme: rol ve sahiplik · 2 dk

**Yap:** Terminalde üç istek (hasta token'ı elimizde):

```bash
# a) Hasta, yönetici ucuna erişmeye çalışıyor
curl -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/admin/stats \
  -H "Authorization: Bearer $TOKEN"

# b) Hasta, BAŞKA bir hastanın randevularını istiyor
curl -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/appointments/patient/1 \
  -H "Authorization: Bearer $TOKEN"

# c) Token olmadan
curl -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/admin/stats
```

**Görülecek:** `403` — `403` — `401`

**Kanıtladığı:** Üç katman ayrı ayrı çalışıyor:
- **401** kimlik yok
- **403 (a)** kimlik var, **rol** yetersiz
- **403 (b)** kimlik ve rol doğru ama **kayıt başkasına ait**

> **Söylenecek cümle:** "İkinci istek önemli: kullanıcının rolü doğru, hasta
> ucuna erişim hakkı var. Ama istediği kayıt başkasına ait. Rol denetimi tek
> başına bunu engellemez; nesne düzeyinde sahiplik kontrolü gerekir."

---

### 8. Yönetici portalı ve rol yönetimi · 2 dk

**Yap:** Yönetici olarak gir.

1. **Özet** sekmesi — rol ve randevu dağılımı
2. **Kullanıcılar ve Roller** → bir hastayı **Yönetici** yap, sonra geri al
3. Kendi satırında rol seçicinin **pasif** olduğunu göster
4. Doktor olan kullanıcının rolünü düşürmeyi dene

**Görülecek:**
3. Kendi rolü değiştirilemiyor
4. *"Bu kullanıcı bir doktor kaydına bağlı; rolü düşürülürse randevuları sahipsiz kalır."*

**Kanıtladığı:** Rol değişikliği yetki matrisini çalışma anında değiştirdiği için
tutarlılık kurallarıyla korunuyor; sisteme kilitlenme ve yetim kayıt engelleniyor.

---

### 9. Kaba kuvvet koruması · 1 dk

**Yap:** Gizli pencerede hasta hesabına **5 kez yanlış şifreyle** girmeyi dene.

**Görülecek:** Kalan deneme hakkı geri sayıyor (4, 3, 2, 1), 5.'de
*"Hesabınız 15 dakika süreyle kilitlendi."* Doğru şifre bile artık çalışmıyor.

**Kanıtladığı:** Kimlik doğrulama katmanı yalnızca şifreyi kontrol etmiyor,
deneme davranışını da izliyor.

> **Dikkat:** Bu adımdan sonra o hesap 15 dakika kilitli kalır. Demonun
> **en sonunda** yap ya da kilidi açmak için şu komutu hazır tut:
> ```sql
> UPDATE users SET failed_login_attempts=0, locked_until=NULL WHERE email='testhasta@mail.com';
> ```

---

### 10. Denetim kaydı: "kim neyi yaptı" · 1 dk

**Yap:** Yönetici portalı → **Denetim Kayıtları** sekmesi.

**Görülecek:** Demo boyunca yapılan her şeyin izi: başarılı/başarısız girişler,
hesap kilitlenmesi, rol değişikliği, randevu iptali, durum değişikliği — kim,
ne zaman, hangi IP'den.

**Kanıtladığı:** Yetki matrisi "kim neyi **yapabilir**" sorusunu yanıtlar;
"kim neyi **yaptı**" sorusunu ise yalnızca denetim kaydı yanıtlar. Yetkinin
kötüye kullanımı ancak izle tespit edilir.

---

### 11. Doğrulama: test takımı · 1 dk

**Yap:** Terminalde `./mvnw test`

**Görülecek:** `Tests run: 225, Failures: 0, Errors: 0`

Bunların 112'si yetki matrisi kontrolüdür: 28 uç nokta, dört aktörle
(kimliksiz · hasta · doktor · yönetici) tek tek denenir. İstersen tek başına
çalıştırıp ölçülen matrisi ekranda gösterebilirsin:

```bash
./mvnw test -Dtest=AuthorizationMatrixTest
```

**Anlat:** Gösterilen her kural otomatik testlerle de doğrulanıyor; zamana bağlı
testler sabit bir referans an kullandığı için sonuçlar günün saatinden bağımsız.

---

## Jüri sorularına hazır cevaplar

**"Şifreler nasıl saklanıyor?"**
BCrypt ile hash'lenerek. Düz metin hiçbir yerde tutulmuyor, yanıtlarda da
taşınmıyor (`@JsonProperty(WRITE_ONLY)`). Veritabanından bir kayıt gösterebilirim.

**"JWT çalınırsa ne olur?"**
Bu, durumsuz kimlik doğrulamanın bilinen bedeli: token sunucuda saklanmadığı
için tek tek iptal edilemez. Sistemde iki telafi var: (1) token 24 saatte
kendiliğinden geçersizleşir, (2) kullanıcı şifresini değiştirdiğinde
`passwordChangedAt` damgası atılır ve o andan önce üretilmiş **tüm** token'lar
reddedilir. Canlı gösterebilirim.

**"Neden JWT? Neden oturum (session) değil?"**
Sunucunun durum tutmaması ölçeklenmeyi kolaylaştırır ve frontend ile backend'in
ayrı çalışmasına uygundur. Bedeli yukarıdaki iptal sorunudur; tezde bu ödünleşimi
tartıştım.

**"Aynı anda iki kişi aynı saate tıklarsa?"**
Uygulama katmanındaki kontrol yarış durumunu tek başına çözmez. Veritabanındaki
kısmi unique index ikinci kaydı reddeder ve bu hata kullanıcıya anlaşılır bir
mesaja çevrilir.

**"Yönetici kötü niyetli olursa?"**
Yönetici kendi rolünü değiştiremez, son yönetici düşürülemez ve yaptığı her
işlem denetim kaydına düşer. Denetim kayıtları için silme veya değiştirme ucu
bilinçli olarak tanımlanmamıştır.

**"Neden kural kataloğu ayrı bir sınıfta?"**
Aynı kurallar iki yerde kullanılıyor: müsait saat listesini üretirken ve kayıt
öncesi doğrularken. Tek kaynaktan okunmazsa ikisi zamanla birbirinden ayrışır —
arayüz bir saati sunarken sunucu reddedebilirdi.

**"Test kapsamı ne kadar?"**
225 test (112'si yetki matrisi kontrolü): kural senaryoları, durum makinesi, rol yönetimi tutarlılığı, HTTP
seviyesinde yetki denetimi, şifre politikası ve kaba kuvvet koruması.

---

## Bir şey ters giderse — Plan B

| Sorun | Çözüm |
|---|---|
| Backend yanıt vermiyor | Eclipse'te yeniden başlat; PostgreSQL'i kontrol et |
| Hasta hesabı kilitli | Yukarıdaki SQL ile kilidi aç, ya da başka bir hesapla devam et |
| Randevu alınamıyor ("aktif randevu limiti") | Hasta portalından bir randevu iptal et |
| Demo verisi kayıp | Hasta portalından profil + randevu 2 dakikada oluşturulur |
| Ekran donuyor / internet yok | `docs/figurler/` altındaki ekran görüntüleriyle anlat — hepsi hazır |

**Altın kural:** Canlı demo patlarsa panikleme, figürlere geç. Ekran görüntüleri
ve diyagramlar aynı akışı anlatacak şekilde hazırlandı.
