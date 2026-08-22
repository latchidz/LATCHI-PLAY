# LATCHI PLAY — بنية TMDB + Providers (مرحلة A+B)

> آخر تحديث: 2026-08-22 — الهوية محفوظة: `com.latchi.play` · Java 17 · minSdk 23 · targetSdk 35 · Gradle 8.7 · AGP 8.5.2

## ما تغيّر في هذه المرحلة

| قبل (3.1) | الآن (3.2) |
|---|---|
| كتالوج من shooflive.net (صفحات HTML) | كتالوج TMDB API (بحث/رائجة/أنواع/تفاصيل/مواسم/حلقات) |
| مشغّل WebView + iframe | مشغّل Media3/ExoPlayer أصلي 100% |
| بدون مفتاح | مفتاح TMDB مجاني من الإعدادات |
| لا يوجد مصدر تشغيل مباشر | Providers: Archive.org + PeerTube + Xtream (سيرفرك) |
| لا failover | Failover تلقائي بين المصادر |

## الملفات الجديدة

- `TmdbClient.java` — عميل TMDB v3 (trending, popular, search, discover, details, seasons, episodes).
- `TmdbDetail.java` — نموذج التفاصيل (فيلم/مسلسل + المواسم).
- `ContentProvider.java` — واجهة موحّدة لمصادر التشغيل.
- `ArchiveOrgProvider.java` — أرشيف الإنترنت: بحث → أفضل ملف mp4 مباشر (تحقّق عمليًا: HTTP 206).
- `PeerTubeProvider.java` — منصات PeerTube (الرابط قابل للتغيير من الإعدادات).
- `XtreamProvider.java` — Xtream Codes API لسيرفرك الخاص (VOD + مسلسلات).
- `ProviderRegistry.java` — ترتيب المصادر حسب الإعدادات مع failover.
- `AppPrefs.java` — تخزين الإعدادات محليًا (مفتاح TMDB، المصدر، بيانات Xtream).
- `SettingsActivity.java` — شاشة الإعدادات.

## الملفات المعاد كتابتها

- `MainActivity.java` — رئيسية TMDB (الرائجة/الأفلام/المسلسلات/الأنواع/البحث) + ترقيم صفحات + زر الإعدادات.
- `DetailActivity.java` — تفاصيل TMDB + المواسم والحلقات داخل الصفحة + زر المشاهدة.
- `SeriesEpisodesPanel.java` — مواسم/حلقات من TMDB.
- `WatchActivity.java` — تشغيل ExoPlayer فقط، بدون WebView؛ مصدر ← فشل ← مصدر تالٍ.
- `CatalogItem.java` — إضافة حقول TMDB (tmdbId, mediaType, overview, rating, year, backdropUrl, genres).
- `FavoritesStore.java` / `HistoryStore.java` — حفظ حقول TMDB (مع استرجاع tmdbId القديمة من pageUrl).

## الإعداد بعد التثبيت

1. **الإعدادات ← مفتاح TMDB**: مفتاح مجاني من `themoviedb.org/settings/api` (API Key v3 أو Read Access Token). يُحفظ على الجهاز فقط.
2. **الإعدادات ← مصدر التشغيل**:
   - `Archive.org` (افتراضي) — أفلام ملكية عامة mp4 مباشرة.
   - `PeerTube` — محتوى مفتوح.
   - `Xtream / IPTV` — سيرفرك الخاص: املأ الرابط + المستخدم + كلمة المرور.
3. الترتيب عند التشغيل: المصدر المختار أولًا ← المصادر المفعّلة ← الاحتياطي.

## حدّ شفاف

- الروابط تؤخذ فقط من واجهات رسمية/مصرّح بها أو من سيرفر تملكه.
- لا استخراج روابط مخفية، لا تجاوز Cloudflare/DRM/CAPTCHA، لا أسرار مصادر غير مصرّح بها.
- المحتوى الحديث (2025/2026) يتطلب مصدرًا تملكه (Xtream/IPTV أو Jellyfin أو Backend رسمي) — لا يوجد بديل مجاني قانوني.
