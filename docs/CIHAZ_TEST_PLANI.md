# POCO X6 Pro / Hiremco Enjoy 14 kabul testi

**Bu liste yapılmış testleri değil, APK derlendikten sonra yapılması gerekenleri gösterir.**

| Alan | Deneme | Kabul |
|---|---|---|
| Kurulum | Aynı APK'yı iki cihaza kur; ad/banner/açılış | LiderTv 0.1.0, çökme yok, TV başlatıcısında görünür |
| Görünüm | Otomatik algılama ve elle Telefon/TV | Kalıcı tercih, okunur afiş ve odak |
| Kaynak | Dosya/HTTPS M3U/Xtream; geçersiz hesap | Doğru sayaç, Türkçe hata, açık parola yok |
| Tek kaynak | Uygulamayı tamamen kapat/aç | Canlı TV açılır; her açılışta ağdan yeniden liste alınmaz |
| Arama | Ana sayfada oyuncu; film içinde aynı sözcük | Genel arama mevcut oyuncu metadatasını, film araması yalnızca o bölümü tarar |
| Hareketler | Kenardan çek, orta alandan kaydır, aşağı çek | Menü/geçiş/arama ayrı; yanlışlıkla yenileme yok |
| Düzen | Uzun bas/sürükle; TV OK/Menü/taşı | Sıra/gizleme/sabitleme yeniden açılışta korunur |
| Film | Tek tık/uzun bas, bilgi/oynat | Tek tık yayını başlatmaz |
| Oynatma | HLS, DASH, TS, MP4, MKV, ses/altyazı | Cihazın destekledikleri sorunsuz; diğerinde anlamlı hata |
| Alternatif | Exo başarısız yayın, VLC donanım/yazılım | Sınırlı deneme; döngü/sızıntı/aynı anda çift ses yok |
| Erişim hatası | 401/403/404, bozuk TLS, çevrimdışı | Yetki ve TLS atlatılmaz; gereksiz sonsuz deneme yok |
| Devam | Film 2 dk izle, çık/aç, motor değiştir | Kaynak ileri sarabiliyorsa yakın konum; canlıda yanlış devam yok |
| Dizi | Bölüm listesi, sıradaki bölüm, tamamlanan bölüm | Doğru bölüm/konum; geçmiş dizi köküne yansır |
| Kilit | PIN koy, kategori kilitle, oturumu kapat | Arama/Tümü/favori/geçmiş/ayrıntı/oynatma sızıntısı yok |
| PIN | 5 yanlış deneme; PIN değiştirme/kaynak silme | Bekleme ve yönetici kontrolü; arka plana alınca kilitlenme |
| Büyük liste | Gerçek 219.906 kayıt; RAM/disk/arama | Ana iş parçacığı donmaz, sayfalama48, bellek/disk yeterli |
| Yenileme | Yarıda iptal, ağ kes, boş liste | Eski liste korunur; başarıda geçmiş/favori doğru eşleşir |
| Yaşam döngüsü | Ev/Geri, ekran döndür, uyku, ağ değiştir | Oyuncu serbest bırakılır, arka planda gereksiz ses yok |
| Kumanda | D-pad, OK, uzun OK, Geri, metin girişi | Dokunmatik gerektiren zorunlu adım yok |

Sonuçları cihaz, Android/WebView sürümü, yayın türü ve hata koduyla kaydedin. Kaynak URL'si, kullanıcı adı, parola veya token içeren ekran görüntüsü/log paylaşmayın. Gerçek kayıt sayısının nedenini ancak sağlayıcı tür ve ham/benzersiz sayaçlarıyla değerlendirin.
