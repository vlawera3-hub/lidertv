# 13 talebin durumu | LiderTv 0.1.0

**"Kodlandı" APK'da derlenip cihazda test edildi demek değildir.** Bu paket bağımsız örnektir; orijinal uygulamanın tamamı değiştirilmiş değildir.

| No | Talep | Bu paketteki karşılık / sınır |
|---|---|---|
| 1 | LiderTv adı | Arayüz, uygulama etiketi, paket ve sürüm adlandırması hazır. |
| 2 | Türkçe | Yeni arayüz ve yazılan kullanıcı mesajları Türkçe. Sağlayıcıdan gelen başlık/açıklama otomatik çevrilmez; orijinal uygulamanın kalan ekranları bu pakette yok. |
| 3 | Tek kaynakla doğrudan canlı | Önizlemede kontrol edildi; yerel açılış koduna eklendi. |
| 4 | Altı sekme, yerel/genel arama | Ayrı sayfalar, yıldız favori, aşağı çekerek arama ve genel oyuncu araması. Oyuncu metadatası eksik kayıtlar için tüm katalog indeksi yok. |
| 5 | Sürükleme, gizleme, iğneleme, son 50 | Menü/gerçek kategori sırası, bağlam menüsü, TV için taşıma düğmeleri; film/dizide son 50. Sabitlenmiş kategoriler sabitlenmeyenlerden önce gelir. |
| 6 | Kenardan klasör aç/kapa | Sol kenar hareketi; TV'de Klasörler düğmesi ve Geri. |
| 7 | Son izlenen/devam | Yerel geçmiş ve konum kodu; demo doğrulandı. Gerçek medya arama/ileri sarma desteği ve süre bilgisi cihazda denenmeli. |
| 8 | Afiş -> bilgi, uzun bas -> oynat | Büyük afişler ve bilgi ekranı; telefon ve kumanda akışları yazıldı. Demo uzun basma testi mevcut. |
| 9 | Yatay bölüm geçişi | Orta alandan yatay kaydırma canlı/film/dizi; kenar hareketi klasör içindir. TV'de sekme ve yön tuşları. |
| 10 | Yanlışlıkla yenileme yok | Üst yenileme yok; Ayarlar > Sağlayıcılar içinde onaylı yenileme ve sayı raporu. Gerçek 219.906 kayıt analiz edilmedi. |
| 11 | Tümü ve sıralama | Her türde Tümü, yeni/eski/A-Z/Z-A; son 50 rafı her zaman en yeni 50'yi tutar. |
| 12 | Oynatıcılar ve hatalar | ExoPlayer/LibVLC/Android MediaPlayer/harici seçim ve sınırlı alternatif deneme kodu. APK ve gerçek medya testi yok; "tüm oynatıcılar" veya "her yayın açılır" iddiası yok. |
| 13 | Ayar görünümü / kilit | Referansa yakın koyu kartlar, kategori PIN kilidi; genel arama/geçmiş/favoriye de uygulanır. Sanal Tümü vb. gerçek kategori kilitlerini devralır. Referanstaki NAS/kayıt/Stremio gibi ek hizmetler yok. |

## Çakışan hareketlerin kuralı

Sol kenarın ilk 32 pikselinden sağa çekme klasör menüsünü açar. Açık menüyü sola çekme kapatır. Orta alanda yatay hareket bölüm değiştirir. Sayfanın başında aşağı çekme arama kutusunu açar, listeyi yenilemez. Uzun basış yaklaşık 600 ms'dir. Menü/kategori uzun basışından sonra sürüklenir; hareket etmeden bırakılırsa düzenleme menüsü gelir. Film/dizi afişinin uzun basışı oynatmadır, kategori kilidiyle karıştırılmaz.

## Test durumu

Saf Java ayrıştırici ve demo mantık testleri ile Chromium arayüz testleri çalıştırıldı. Sonuçlar `qa/` içinde. Android SDK/Gradle bağımlılık çözümü, APK imzalama, cihaz kurulumu, gerçek hesap girişi, kumanda ve medya codec testleri yapılmadı.


## Kaydedilen yerel test sayilari

Saf Java: 17/17. Demo veri/PIN mantigi: 24/24. Chromium arayuz: 10/10. Chromium dokunma/kumanda olayi: 7/7. Sentetik masaustu SQLite: 10/10. Yedi Java dosyasi sozdizimi olarak ayrisildi; Android tip denetimi ve APK derlemesi yapilmadi.

SQLite denemesinde 219.906 yapay girdi, 200.000 benzersiz kayit ve 19.906 yinelenen kayit kullanildi. Bu sayilar kullanicinin yayin listesinden elde edilmedi. Sonuclar cihaz hizini veya gercek saglayici uyumlulugunu kanitlamaz.
