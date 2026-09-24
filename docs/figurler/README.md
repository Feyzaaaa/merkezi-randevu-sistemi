# Tez Figürleri

Bu klasördeki görseller tez metnine doğrudan eklenebilir. Her figürün kaynağı
yanındadır; içerik değişirse yeniden üretilebilir.

## Diyagramlar

| Dosya | Şekil | İçerik |
|---|---|---|
| `01-er-diyagrami.png` | Şekil 1 | Veritabanı varlık-ilişki diyagramı (8 tablo, yabancı anahtarlar) |
| `02-katmanli-mimari.png` | Şekil 2 | Katmanlı mimari; 401/403 kararlarının hangi katmanda verildiği |
| `03-istek-akisi.png` | Şekil 3 | Dört senaryoda istek akışı: token yok / rol yetersiz / başkasının kaydı / yetkili |
| `04-durum-makinesi.png` | Şekil 4 | Randevu durum geçişleri ve iptal yetkileri |
| `05-cakisma-kurallari.png` | Şekil 5 | Randevu oluşturmada kural (R1–R9), çakışma ve veritabanı garanti katmanı |

## Ekran görüntüleri

| Dosya | İçerik |
|---|---|
| `06-ekran-karsilama.png` | Karşılama ekranı — rol seçimi |
| `07-ekran-hasta-girisi.png` | Hasta giriş ekranı (her rolün ayrı girişi) |
| `08-ekran-hasta-portali.png` | Hasta portalı — sağlık profili, tahliller, randevu geçmişi |
| `09-ekran-doktor-portali.png` | Doktor portalı — izin günleri, hasta listesi, durum işlemleri |
| `10-ekran-yonetici-ozet.png` | Yönetici portalı — rol ve randevu dağılımı |
| `11-ekran-yonetici-roller.png` | Yönetici portalı — kullanıcı ve rol yönetimi |
| `12-ekran-denetim-kayitlari.png` | Yönetici portalı — denetim kayıtları (kim, ne zaman, neyi, nereden) |
| `13-ekran-kayit.png` | Kayıt ekranı — şifre politikası bilgilendirmesi |
| `14-ekran-oneriler.png` | Optimizasyon motoru — hasta için önerilen randevular ve gerekçeleri |

## Diyagramları yeniden üretme

Kaynaklar Mermaid biçimindedir (`.mmd`). İki yol var:

**1. Tarayıcıda** — `.html` dosyalarını çift tıklayarak açın; diyagram render edilir,
sağ tık ile kopyalayabilirsiniz.

**2. Komut satırında** (bu görseller böyle üretildi):

```bash
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --window-size=1500,1450 --virtual-time-budget=8000 \
  --screenshot=01-er-diyagrami.png "file://$PWD/01-er-diyagrami.html"
```

Diyagram kesiliyorsa `--window-size` yüksekliğini artırın.

**3. Çevrimiçi** — `.mmd` içeriğini <https://mermaid.live> adresine yapıştırıp
PNG veya SVG olarak dışa aktarabilirsiniz (Word'e SVG daha net gelir).

## Ekran görüntülerini yeniden alma

Ekran görüntüleri için backend (8081) ve frontend (3000) çalışır durumda olmalıdır.
Oturum gerektiren sayfalar için, `mhrs-frontend/public/` içine geçici bir yardımcı
sayfa konup `localStorage`'a oturum yazıldıktan sonra hedef sayfaya yönlendirilmiştir;
görüntüler alındıktan sonra bu dosya silinmiştir. Yönetici portalında sekme,
`?tab=users` / `?tab=audit` parametresiyle seçilebilir.
