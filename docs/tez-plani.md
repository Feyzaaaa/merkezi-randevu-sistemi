# Tez Yazım Planı

Bu belge, geliştirilen sistemdeki her malzemeyi tez metnindeki yerine bağlar:
hangi bölümde ne anlatılacak, hangi figür nereye girecek, hangi iddianın kanıtı
nerede duruyor.

---

## Önce: anlatının omurgası

Sistemde beş ayrı konu var gibi görünebilir (çakışma yönetimi, yetkilendirme,
optimizasyon, bağlam farkındalı erişim, bozulma yönetimi). **Bunları beş eşit
başlık gibi sunma.** Jüri "asıl katkın hangisi?" diye sorar.

Tek bir omurga kur:

```
EKSEN 1 — ÇAKIŞMA YÖNETİMİ
    Önleme      : randevu ALINIRKEN doğan çakışmalar        (R1–R11)
    Seçme       : geçerli alternatifler arasından en uygunu  (optimizasyon motoru)
    Çözme       : plan kurulduktan SONRA gelen bozulma       (minimal müdahale)

EKSEN 2 — YETKİLENDİRME
    Kimlik      : 401
    Rol         : 403
    Sahiplik    : 403  — "bu kayıt kimin?"
    Bağlam      : 403  — "bu kişi ŞU ANDA erişmeli mi?"      (ABAC)
    Hesap verme : denetim kaydı — "kim neyi yaptı?"
```

Optimizasyon ayrı bir konu değil, çakışma yönetiminin devamı. ABAC ayrı bir konu
değil, yetkilendirmenin derinleşmesi. Metin bunu böyle kurarsa **genişlik değil
derinlik** görünür.

Kod zaten böyle katmanlı: her katman bir öncekini kullanıyor. Metin de öyle olmalı.

---

## Bölüm planı

### 1. Giriş

- **Problem:** Hastane randevu sistemlerinde iki temel zorluk — çakışan talepler
  ve hassas sağlık verisine erişim denetimi.
- **Bağlam:** MHRS benzeri merkezi bir randevu sistemi.
- **Tezin iki ekseni** (yukarıdaki omurga şeması).
- **Katkılar** — madde madde, somut:
  1. Senaryo tabanlı, genişletilebilir bir kural kataloğu (R1–R11) ve bunun
     üç katmanda uygulanması
  2. Yarış durumunda veritabanı garantisinin **ölçülmüş** gerekliliği
  3. Çok ölçütlü randevu seçimi; %100 doluluğun hedef olmadığının gösterilmesi
  4. Rol tabanlı yetkilendirmenin nesne sahipliği ve bağlamla derinleştirilmesi
  5. Program bozulmasında plan kararlılığı / hasta rahatsızlığı ödünleşiminin
     ölçülmesi
- **Tezin organizasyonu** (bölümlerin kısa özeti).

### 2. Literatür Taraması

Bu bölümü **ben yazamam** — okumadığım kaynağı kaynak gösteremezsin. Ama neyi
arayacağını söyleyebilirim. Şu başlıklarda tarama yap:

| Konu | Aranacak terimler |
|---|---|
| Randevu çizelgeleme | *appointment scheduling*, *outpatient clinic scheduling*, *slot allocation* |
| Kapasite ve aşırı rezervasyon | *overbooking*, *clinic utilization*, *no-show aware scheduling* |
| İptal/gelmeme tahmini | *no-show prediction*, *appointment cancellation prediction*, *lead time no-show* |
| Yeniden çizelgeleme | *minimal perturbation scheduling*, *schedule disruption management*, *schedule stability* |
| Erişim denetimi | *RBAC*, *ABAC*, *context-aware access control*, *break-glass access* |
| Sağlık verisi mahremiyeti | *EHR access control*, *audit logging*, *treatment relationship* |

Her başlıkta 3–5 kaynak yeter. **Okumadığın makaleyi kaynakçaya koyma** — jüri
tek bir soruyla anlar.

### 3. Sistem Tasarımı ve Yöntem

| Alt bölüm | İçerik | Figür |
|---|---|---|
| 3.1 Mimari | Katmanlı yapı, istemci/sunucu/veritabanı sorumlulukları | **Şekil 2** |
| 3.2 Veri modeli | 8 tablo, ilişkiler, kısmi unique index'ler | **Şekil 1** |
| 3.3 Kural kataloğu | R1–R11: saf zaman kuralları / veriye bağlı kurallar ayrımı | tablo (README'den) |
| 3.4 Üç katmanlı çakışma önleme | Arayüz (kolaylık) → uygulama (kural) → veritabanı (garanti) | **Şekil 5** |
| 3.5 Randevu durum akışı | Durum makinesi ve geçiş kısıtları | **Şekil 4** |
| 3.6 Optimizasyon motoru | Dört ölçütlü maliyet fonksiyonu, asimetrik doluluk cezası, iptal riski modeli, çeşitlilik kısıtı | — |
| 3.7 Bozulma yönetimi | Minimal müdahale, kayma terimi, bozulma bütçesi, rezervasyon (R11) | — |
| 3.8 Erişim denetimi | Dört katman; 401/403 ayrımı; ABAC koşulları; acil erişim | **Şekil 3** |
| 3.9 Denetim kaydı | İzlenen olaylar, yalnızca-okunur tasarım | ekran görüntüsü 12 |

**Yazarken dikkat:** her tasarım kararının *neden* öyle olduğunu yaz. Örneğin
"hedef doluluk %85" değil, "**%100 doluluk mola, gecikme ve acil hasta için pay
bırakmaz; tek bir gecikme zincirleme olarak günü kaydırır — bu yüzden hedef %85
ve ceza asimetriktir**". Tezi tez yapan bu gerekçelendirmedir.

### 4. Uygulama ve Bulgular

Bu bölüm **en kolay yazılacak bölüm**, çünkü sayılar hazır. Buradan başla.

| Alt bölüm | İçerik | Kaynak |
|---|---|---|
| 4.1 Geliştirilen sistem | Üç portal, ekran görüntüleriyle | **Şekil 6–14** |
| 4.2 Test kapsamı | 244 backend + 9 frontend testi, sınıf bazında tablo | README test tablosu |
| 4.3 **Deney 1: Yarış durumu** | 20 eşzamanlı talep; index açık/kapalı | tablo aşağıda |
| 4.4 **Deney 2: Doluluk hedefi** | Üç doktor, farklı dolulukta; motorun tercihi | tablo aşağıda |
| 4.5 **Deney 3: Bozulma bütçesi** | K=0…12 eğrisi | tablo aşağıda |
| 4.6 Yetki matrisinin doğrulanması | 28 uç × 4 aktör = 112 kontrol | README matrisi |
| 4.7 Geliştirme sürecinde tespit edilen kusurlar | aşağıda | — |

#### Deney tabloları (metne doğrudan aktarılabilir)

**Deney 1 — Eşzamanlı randevu talebi**

| Deney | Veritabanı garantisi | Kabul edilen | Kaydedilen | Mükerrer |
|---|:--:|:--:|:--:|:--:|
| A | açık (kısmi unique index) | 1 | 1 | 0 |
| B | kapalı (yalnızca uygulama kontrolü) | 10 | 10 | **9** |

*Yorum: "önce kontrol et, sonra kaydet" mantığı 20 eşzamanlı istekte 10 talebin
kontrolü birlikte geçmesine engel olamadı.*

**Deney 2 — Doluluk maliyeti**

| Doluluk | Maliyet |
|---|---|
| %85 (hedef) | 0.00 ← tercih edilen |
| ~%5 (neredeyse boş) | 0.94 |
| ~%98 (aşırı yüklü) | 1.73 |

*Senaryo: üç doktor sırasıyla ~%95 / ~%85 / %0 dolulukta. Motor, bir gün fazla
beklemeyi göze alarak %85'teki doktoru seçti.*

**Deney 3 — Bozulma bütçesi**

| Politika | Çözülen | Ek rahatsız | Ort. kayma | Kararlılık |
|---|---:|---:|---:|---:|
| Toplu iptal (referans) | 0 | – | – | – |
| Minimal müdahale (K=0) | 12 | 0 | 41.0 sa | %97.0 |
| Kademeli (K=3) | 15 | 3 | 32.8 sa | %96.2 |
| Kademeli (K=6) | 18 | 6 | 27.3 sa | %95.5 |
| Kademeli (K=12) | 24 | 12 | 20.5 sa | %94.0 |

*Yorum: bütçe arttıkça kayma azalıyor ama rahatsız edilen hasta sayısı ve plan
kararsızlığı artıyor. Tek bir doğru politika yok, bir ödünleşim var.*

#### 4.7 Geliştirme sürecinde tespit edilen kusurlar

Bu alt bölüm alışılmadık ama **çok değerli**: testlerin ve deneylerin gerçekten
işe yaradığını gösterir. Her biri bir cümlelik anlatıyla:

| Kusur | Nasıl bulundu |
|---|---|
| Sunucu tarafı tarih doğrulaması yoktu (2020'ye randevu kabul ediliyordu) | API'ye doğrudan istek |
| Dört uç noktada rol denetimi eksikti | yetki matrisi çıkarılırken |
| Rol reddi 401 dönüyordu (403 yerine) | canlı yetki testi |
| `toLowerCase()` Türkçe yerel ayarda kuralı atlatıyordu | şifre politikası testi |
| Hibernate'in enum CHECK kısıtı denetim kayıtlarını sessizce düşürüyordu | denetim günlüğü boş çıkınca |
| Global hata yöneticisi 405'i 500'e çeviriyordu | kaldırılan uç doğrulanırken |
| Optimizasyon motoru hastayı yanlış uzmanlığa atıyordu | bozulma deneyinde kayma 0 çıkınca |
| Aynı slot iki hastaya birden atanıyordu | veritabanı unique index'i |

### 5. Tartışma

Burada tasarım kararlarını **savun**. Her biri bir paragraf:

1. **İstemci tarafı doğrulama bir kolaylıktır, güvence değildir.** Arayüz kötü
   seçenekleri gizler; kuralı uygulayan sunucudur, garantiyi veren veritabanıdır.
2. **%100 doluluk hedef değildir.** Kırılgan plan, zincirleme gecikme, acil hasta payı.
3. **Kararlılık ile optimallik çelişir.** Daha iyi bir yerleşim uğruna kaç hastayı
   rahatsız etmeye değer? Deney 3 bunun sayısal karşılığı.
4. **Durumsuz kimlik doğrulamanın bedeli.** JWT iptal edilemez; şifre değişikliği
   damgası ve kısa ömür bunun telafisi.
5. **Katı politika zarar verebilir.** Acil erişim engellenmez, hesap sorulur.
6. **Rol denetimi sağlık verisinde yetmez.** Tedavi ilişkisi + zaman + görev durumu.

**Sınırlar bölümünü mutlaka yaz** (jüri zaten soracak, önce sen söyle):

- Deneyler sentetik veri üzerinde yapıldı; gerçek hastane yükü farklı davranabilir.
- İptal riski modeli pratikte hep soğuk başlangıçta çalıştı; sistemde yeterli
  geçmiş veri birikmedi. Model doğrusaldır, öznitelik etkileşimlerini yakalamaz.
- Kademeli taşıma tek seviyede kesildi; daha derin zincirler incelenmedi.
- Ağırlıklar uzman görüşüyle değil, makul varsayımlarla seçildi; duyarlılık
  analizi yapılabilir (parametreler ayarlanabilir bırakıldı).
- `ddl-auto=update` üretim için uygun değildir; şema geçişleri elle yazıldı.

### 6. Sonuç ve Öneriler

- İki eksende ne yapıldığının özeti
- Ölçülen üç sonucun tek paragrafta tekrarı
- Gelecek çalışma: gerçek veriyle model eğitimi, doktor bazında mesai
  parametreleri, çok seviyeli kademeli taşıma, bütçe eğrisinin geniş
  parametre uzayında taranması

### Ekler

- **Ek A:** Yetki matrisi (28 uç × 4 aktör) — README'den
- **Ek B:** Kural kataloğu R1–R11 — README'den
- **Ek C:** Kurulum ve çalıştırma adımları — README'den
- **Ek D:** Test sınıfları ve kapsamı

---

## Her iddia → hangi kanıt

Metinde bir iddia yazarken yanına kanıtını koy. Karşılığı olmayan iddia yazma.

| İddia | Kanıt |
|---|---|
| İstemci doğrulaması yeterli değildir | Kusur tablosu satır 1 + Şekil 5 |
| Uygulama katmanı yarış durumunu çözmez | **Deney 1** |
| %100 doluluk iyi bir hedef değildir | **Deney 2** + `UtilizationCostTest` |
| Bozulmada kararlılık ile kayma çelişir | **Deney 3** |
| Rol denetimi tek başına yetmez | Yetki matrisi + ABAC bölümü |
| Erişim politikası hesap verebilir | Denetim kaydı ekran görüntüsü |
| Sistem kurallara uygun çalışıyor | 253 test |

---

## Yazma sırası önerisi

1. **Bulgular (4)** — sayılar hazır, en kolay başlangıç, momentum verir
2. **Sistem Tasarımı (3)** — README'nin çoğu zaten yazılmış durumda
3. **Tartışma (5)** — tasarım gerekçeleri kodun yorumlarında duruyor
4. **Literatür (2)** — tarama zaman alır, paralel yürüt
5. **Giriş ve Sonuç (1, 6)** — en son yaz; ne anlattığını bildikten sonra

## Figürler

`docs/figurler/` klasöründe hazır: 5 diyagram + 9 ekran görüntüsü.
Word'e koyarken diyagramları <https://mermaid.live> üzerinden **SVG** olarak
almak PNG'den daha net sonuç verir (`.mmd` kaynakları aynı klasörde).

## Savunma

`docs/savunma-senaryosu.md` — 11 adımlık demo akışı, jüri sorularına hazır
cevaplar ve bir şey ters giderse plan B.
