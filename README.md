# Astro Miner

*The Farmer Was Replaced* tarzında bir **programlama oyunu** (Android). Asteroit yüzeyindeki
madenci drone'unu Python benzeri bir dille programlıyorsun: kaya kaz, kristal yetiştir,
kaynak biriktir, yeni komutlar ve dil özellikleri aç, daha verimli kod yaz.

## Oyun döngüsü

1. Başta 1×1 alan ve tek komut var: `mine()`. Çalıştır'a bas → 1 Taş.
2. Taşlarla **Kilitler** ekranından ilerlemeyi satın al:
   `while` → Genişletme (`move`, 3×3 alan) → Sensörler → `if` → Operatörler → Kristaller →
   Konum → Değişkenler → `for` → Fonksiyonlar → Listeler. Ayrıca Hız ve alan büyütme seviyeleri var.
3. Her yeni kaynak yeni bir algoritma problemi getirir. Örneğin kristaller, komşu her
   kristal için **2 kat yavaş** büyür, bu da dama tahtası deseni gibi yerleşimleri ödüllendirir.

```python
# Örnek: alanı tara, olgun olanı kaz
while True:
    for i in range(get_world_size()):
        if can_mine():
            mine()
        move(East)
    move(North)
```

## Proje yapısı

```
core/   Saf Kotlin/JVM modülü: Android'e bağımlı değil, bilgisayarda test edilir
  lang/       Python alt kümesi: Lexer → Parser → AST → Interpreter
              Features.kt: kilitli dil özelliklerini çalıştırmadan önce yakalar
  game/       World (ızgara, büyüme, drone), Balance (tüm sayılar),
              Commands (komut belgeleri), Game (dil ↔ dünya bağlantısı)
  progress/   Unlocks (kilit ağacı ve fiyatlar), Progress (envanter, seviyeler), SaveData (JSON)
app/    Android arayüzü (Jetpack Compose)
  GameViewModel   Çalıştırma, hız, adım adım mod, kayıt
  ui/             WorldView (Canvas), CodeEditor, Kilitler ve Belgeler ekranları
```

### Önemli tasarım kararları

- **Kendi yorumlayıcımız.** Ağaç üzerinde yürüyen `suspend` tabanlı bir yorumlayıcı.
  Her `move()` / `mine()` çağrısı askıya alınabilir, böylece arayüz animasyon için bekleyebilir.
  Hız ayarı, adım adım çalıştırma ve durdurma bu sayede kolay.
- **Oyun zamanı = tick.** Her eylem tick harcar (`Balance.ACTION_TICKS`). Büyüme tick ile ölçülür
  ve tembel hesaplanır. Simülasyon deterministik ve test edilebilir. Arayüz tick'leri gerçek
  zamana çevirir (1× hızda 1 tick = 2 ms).
- **Kilitli dil.** `while`, `if`, değişkenler gibi özellikler satın alınana kadar derleme
  aşamasında reddedilir ve oyuncuya hangi kilidi açması gerektiği söylenir.
- **Mobil editör.** Hızlı ekleme çubuğu (açılan komutlar otomatik eklenir), otomatik girinti,
  sözdizimi renklendirme ve çalışan satırın vurgulanması.

## Derleme

Gereksinimler: Android Studio (Ladybug veya daha yeni), JDK 17+, Android SDK 36.

```bash
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew :core:test                # Dil ve oyun mantığı testleri
```

Veya projeyi Android Studio'da açıp ▶ ile cihaza veya emülatöre yükle.

## Yol haritası

- [ ] Drone hareket animasyonu, kazma efektleri
- [ ] Yeni kaynaklar: enerji çekirdeği (en değerliyi bul), saflık sıralaması (sıralama algoritmaları),
      mağara labirenti (yol bulma), kargo vagonları (yılan)
- [ ] Birden fazla kod dosyası ve `import`
- [ ] Başlangıç için blok tabanlı editör modu
- [ ] Başarımlar ve "en hızlı çözüm" liderlik tabloları
