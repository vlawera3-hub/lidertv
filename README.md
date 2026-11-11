# LiderTv 0.1.0 | Telefon + TV başlangıç örneği

**Bu arşiv kurulabilir APK değildir. Android uygulaması burada derlenmedi; POCO X6 Pro ve Hiremco Enjoy 14 cihazlarında denenmedi.**

Paket, çalışan etkileşimli bir tarayıcı önizlemesi ve onun için yazılmış Android kaynak projesini içerir. M3UAndroid'in mevcut Compose ekranlarına yapılmış tamamlanmış bir yama değildir. Ayrı bir Android uygulaması olarak hazırlanmıştır. Orijinal depoya yeni modül ekleme aracı da sunulur; bu işlem mevcut ekranları değiştirmez.

## Önce önizlemeyi açın

`docs/LiderTv_Onizleme.html` dosyasını Chrome veya Edge ile açın. Kurulum ve hesap gerekmez. Sağ üstteki **TV görünümü / Telefon görünümü** düğmesiyle geçiş yapın. Telefon düzeni dar pencerede daha iyi görünür. Ana sayfa, Canlı TV, Filmler, Diziler, Favoriler ve Ayarlar bölümlerini deneyin.

Bu dosyada **yayın oynatma temsilidir**. Afişler ve oyuncular örnek veridir; cihazınızdaki listeyi kullanmaz. Tek kaynakla açılış Canlı TV ekranına yapılır. Genel oyuncu aramasını Ana sayfada `Deniz` yazarak deneyebilirsiniz. Filmler aramasında aynı metin oyuncu alanını aramaz. Filmlere tek tık bilgi, uzun basış oynatma akışını açar.

Tarayıcıda PIN işlemleri için WebCrypto gerekir. Tarayıcı yerel dosyada bunu kısıtlarsa dosyayı kendi bilgisayarınızda localhost üzerinden sunun: proje klasöründe `python -m http.server 8000`, sonra `http://localhost:8000/docs/LiderTv_Onizleme.html`. Bu yalnızca önizleme içindir. Tarayıcı verileri silinebilir; Android uygulamasının veri tabanı ayrıdır.

## Gerçek Android kodunda ne var?

Tek uygulama kimliği: `tr.lidertv.app`; sürüm: `0.1.0`; minimum SDK 26, derleme/hedef SDK 35. Dokunmatik zorunlu değildir; telefon ve TV başlatıcıları tanımlıdır. Otomatik algılama yanlışsa elle TV görünümü seçilebilir. Bu yapılandırma cihaz uyumluluk testinin yerine geçmez.

Yerel arayüz Android WebView içinde çalışır; kaynaklar ve büyük listeler Android SQLite/FTS veri tabanında tutulur. Medya gerçek Android oynatıcılarıyla açılır: Media3 ExoPlayer, LibVLC donanım/yazılım, Android MediaPlayer ve cihazdaki harici oynatıcılar. Arayüz JavaScript'i yalnızca uygulamanın kendi varlık alanından yüklenir; rastgele web sayfaları yerel köprüye erişmez.

M3U adresi, yerel M3U dosyası ve Xtream kaynak girişi için kod yazıldı. Yerel uygulama **boş kaynakla** başlar. Orijinal M3UAndroid verisi/favorileri otomatik alınmaz. Liste parolasını GitHub'a, test ekran görüntüsüne veya sohbet dosyasına koymayın.

## Önemli sınırlar

- **Android derleme ve gerçek yayın denemesi yapılmadı.** Java sözdizimi kontrolü, Android bağımlılıklerinin doğru derlendiğini göstermez. İlk derlemede uyumluluk düzeltmeleri gerekebilir.
- Bu, orijinal uygulamanın tüm özelliklerini koruyan tamamlanmış bir fork değildir. EPG, Chromecast, kayıt, NAS, Stremio, QR kurulumu ve orijinal veri göçü bu örnekte yoktur. Referanslardaki her ayar kopyalanmadı; çalışmayan boş düğmeler eklenmedi.
- Genel oyuncu araması sadece listeyle gelen veya ayrıntı ekranı açılırken sağlayıcıdan alınan yerel oyuncu bilgisini tarar. Tüm kataloğun oyuncularını baştan tarayan TMDB/IMDb entegrasyonu yoktur. Oyuncu bilgisi olmayan kayıtlar adla aranabilir.
- M3U tür bilgisi yoksa Canlı/Film/Dizi ayrımı URL ve klasör adından tahmin edilir; her sağlayıcıda kusursuz değildir. Xtream tür bilgisi tercih edilir. M3U dizi bölümleri her zaman dizi kökü altında birleşmez; Xtream bölüm listesi ayrıntıda alınır.
- Son eklenen sırası sağlayıcınin `added` / `last_modified` bilgisini, yoksa cihazda ilk görülme zamanını kullanır. İlk toplu aktarımda gerçek yayınlanma tarihi bilinmez. Aynı tarihte liste sırası belirleyicidir.
- 219.906 gerçek kaydınız incelenmedi. Aktarım raporu ham/benzersiz/yinelenen/atlanan/türü tahmin edilen sayıları ayırır. Aynı kaynak+tür+aynı URL tekilleştirilir; farklı token/kalite/URL otomatik olarak birleştirilmez. Aynı yayının birden fazla kategoriye atanması son kategoriye indirgenebilir.
- Sayfalar 48 kayıtla açılır; tüm 219.906 afiş aynı anda belleğe alınmaz. Yenileme başarılı olana kadar eski nesil korunur; geçici olarak iki nesil için disk alanı gerekir. Gerçek Android performans testi yapılmadı.
- Kaldığı yerden devam yereldir. Telefon/TV arasında otomatik eşitleme yoktur. Kaynağın ileri sarma desteği gerekir; harici oynatıcı konumu otomatik geri alınamaz. Sonraki dizi bölümü seçimi vardır, otomatik aralıksız sonraki bölüm oynatma yoktur.
- PIN klasör içeriğini arama/favori/geçmiş/ayrıntı/oynatma yollarında da süzer. Yerel PIN uygulama verisi silinerek sıfırlanabilir; bir DRM/güvenlik kasası değildir. `Tümü`, `Son izlenenler` gibi sanal klasörler ayrıca kilitlenmez; asıl kategori kilitlerini uygular.
- Tüm yayınları açma garantisi yoktur. Hesap yetkisi, kapalı sunucu, bozuk bağlantı, DRM ve TLS sertifikası atlatılmaz. Hata kaynağı biliniyorsa yazılır; bilinmiyorsa tahmin kesin neden diye sunulmaz.

## APK nasıl üretilir?

### GitHub Actions (hazır iş akışı)

Bu proje dosyalarını, `.github` dahil, **yeni bir özel GitHub deposunun köküne** koyun. Repo içinde `LiderTv_0.1.0/` adlı ek bir üst klasör olmamalı. GitHub Actions > **LiderTv Android deneme APK** > Run workflow iş akışını çalıştırın. Derleme başarılı olursa sonuçtaki `LiderTv_0.1.0_Telefon_TV_Deneme` dosyası içinde APK bulunur. Bu iş akışı burada çalıştırılmamıştır; başarılı çıktı garantisi verilmez.

APK **debug/deneme imzalı** olacaktır. GitHub'da farklı çalıştırmalarda oluşan imza değişebilir; mevcut kuruluma güncelleme yapılamayabilir. Kalıcı dağıtımda ayrı, korunmuş bir imza anahtarı yapılandırılmalı. Uygulamayı kaldırmak yerel listeleri/geçmişi siler. İmza anahtarını depoya yüklemeyin.

### Windows / Android Studio

JDK 17 ve Android SDK Platform 35 kurulu olmalı. `JAVA_HOME` JDK'ya, `ANDROID_HOME` SDK'ya işaret etmelidir. Android Studio'nun SDK Manager ekranından SDK 35 ve Build Tools 35 kurulabilir. `DERLE_WINDOWS.bat`, Gradle 8.11.1'i resmı adresten indirip sabit SHA256 ile doğrular, standart wrapper dosyalarını üretir ve derlemeyi dener. Android SDK/JDK kurulumu yapmaz. İnternet bağlantısı gerekir. Bu betik Windows'ta çalıştırılmadı.

Beklenen çıktı: `output/LiderTv_0.1.0_Telefon_TV.apk`. Paket henüz bu dosyayı içermez. Arşivde başlangıçta `gradle-wrapper.jar` yoktur; betik/iş akışı tarafından üretilir. Linux/macOS'ta Gradle 8.11.1 ve SDK kurulduktan sonra `gradle :app:assembleDebug` kullanılabilir.

## M3UAndroid içine ekleme (isteğe bağlı geliştirici yolu)

Orijinal depoyu klonlayıp gerekli alt modüllerini hazırladıktan sonra:

```text
python tools/add-to-m3uandroid.py /M3UAndroid/klasor/yolu
```

Araç yeni `app/lidertv` modülünü ekler, `settings.gradle.kts.lidertv-backup` oluşturur. Orijinal `smartphone`/`tv` ekranlarına dokunmaz. Geri almak için yedeği geri yükleyip eklenen modülü kaldırın. Orijinal projenin kendi Gradle wrapper'ı ile `:app:lidertv:assembleDebug` denenebilir; bu bütünleştirme gerçek depoda derlenmedi. Standalone proje, orijinal deponun eksik alt modüllerine bağımlı değildir.

## Testlerin kapsamı

`qa/` altında tarayıcı ve saf mantık test sonuçları, ekran görüntüleri ve Java sözdizimi raporu vardır. Bunlar Android cihaz testi değildir. `tools/test-core.sh` saf Java ayrıştırıcıyı derler; `core-tests/demo-tests.js` demo veri mantığını Node ile, `tools/browser-check.py` arayüzü Chromium ile kontrol eder. `docs/CIHAZ_TEST_PLANI.md` gerçek cihaz kabul listesidir.

Kaynak yapısı: `MainActivity.java` yerel köprü ve kumanda; `PlayerActivity.java` oynatıcılar; `Store.java` veri tabanı/arama; `Importer.java` M3U/Xtream; `Vault.java` PIN ve saklanan adreslerin şifrelenmesi; `M3uParser.java` liste ayrıştırma; `assets/ui` arayüz. Tüm sürüm etiketleri 0.1.0 olarak tutulmuştur.
