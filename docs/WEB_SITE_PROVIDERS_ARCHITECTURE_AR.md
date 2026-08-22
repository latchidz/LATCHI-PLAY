# LATCHI PLAY — مواقع المحتوى: بنية ونتائج الفحص (مرحلة C)

> التاريخ: 2026-08-22 — البنية: LATCHI PLAY UI ← PlaybackResolver ← ProviderRegistry ← ContentProvider ← PlaybackSource ← Media3 ← Native Player

## المواقع المدعومة (حسب فحص عملي)

| الموقع | الكتالوج داخل التطبيق | التشغيل المباشر | ملاحظة |
|---|---|---|---|
| **topcinemaa.co** | ✅ (رئيسية / أفلام / مسلسلات / بحث + ترقيم) | غير متاح في HTML العام | البث عبر embed محمي `down.vidtube.one` — لا نتبعه |
| **tv10.egydead.live** | ✅ (رئيسية / أفلام / مسلسلات / بحث) | غير متاح في HTML العام | البث عبر endpoint بمفاتيح anti-bot (`data-cp-host`) — لا نكسره |
| **mycima.cafe** | ❌ | ❌ | Cloudflare challenge نشط (403) — لا نتجاوز الحماية |

**القاعدة المطبقة:** المصدر يُدمج كـ Provider في البنية الموحدة. عند طلب "مشاهدة" يُجرَّب تلقائيًا مع كل المصادر (Failover)، وإن لم يوجد رابط مباشر في HTML العام تُعاد حالة `PLAYBACK_UNAVAILABLE` بدل محاولة كسر الحماية.

## الملفات الجديدة في هذه المرحلة

- `PlaybackResolver.java` — طبقة مستقلة: ترتيب المصادر، مهلة زمنية لكل مصدر، تحقق من الرابط، عدد محاولات محدود، تصنيف الخطأ (unavailable بدل تجاوز).
- `HtmlFetcher.java` — جلب صفحات عامة + استخراج الروابط المباشرة الظاهرة في HTML فقط (`.mp4/.m3u8/.mpd/.webm`) + تحقق Range.
- `TopCinemaaProvider.java` — كتالوج TopCinemaa (وردبريس) + محاولة تشغيل من الروابط المباشرة الظاهرة.
- `EgyDeadProvider.java` — كتالوج EgyDead (وردبريس) + محاولة تشغيل من الروابط المباشرة الظاهرة.
- `MyCimaProvider.java` — مسجّل مع إفادة فورية "غير متاح" (Cloudflare).
- `SiteCatalogActivity.java` — شاشة تصفح موحّدة لأي Provider يدعم الكتالوج (رئيسية/أفلام/مسلسلات/بحث + ترقيم + حالات تحميل/خطأ/فارغ).

## تحسينات على الطبقات الموجودة

- `CatalogItem` — إضافة `providerId` + `contentId` (تحديد الهوية دون الاعتماد على الـ URL).
- `ContentProvider` — طرق كتالوج افتراضية (home/movies/series/search + supportsCatalog) مع بقاء resolve للتشغيل.
- `ProviderRegistry` — تسجيل كل المصادر + `catalogProviders()` + `byId()`.
- `MainActivity` — زر "المواقع" (اختيار موقع ← تصفح) + صف "متابعة المشاهدة" أفقي في الرئيسية.
- `WatchActivity` — يعتمد على `PlaybackResolver` (مهلة + failover) + عدّاد تلقائي "الحلقة التالية (5…)" عند تفعيل الخيار.
- `SeriesEpisodesPanel` — بطاقات حلقات بصور مصغّرة (thumbnails من TMDB) بدل أزرار نصية فقط.
- `PlaybackController` — أزرار رجوع/تقديم 10 ثوانٍ + زر ترجمة في المشغّل.
- `SettingsActivity` — اختيار مصدر يشمل المواقع الجديدة + مفتاح "التشغيل التلقائي للحلقة التالية" + زر مسح السجل + سطر الإصدار.
- `DetailActivity` — عرض تفاصيل عناصر المواقع (غير TMDB) مباشرة دون جلب TMDB.

## نتائج الفحص العملي

- استخراج البطاقات: TopCinemaa 62 بطاقة / EgyDead 77 بطاقة من الصفحة الرئيسية (عناوين + صور + روابط) ✓
- البحث: `?s=mutiny` يرجّع نتائج في الموقعين ✓
- الترقيم: TopCinemaa `/page/N/` ✓ — EgyDead بدون ترقيم رقمي (صفحة واحدة لكل قسم)
- التشغيل المباشر: لا توجد روابط `.mp4/.m3u8` ظاهرة في HTML الثابت لأي من الموقعين — البث عبر embed محمي (ملخص أعلاه)

## شفافية كاملة

المستخدم يوافق على استعمال هذه المواقع، لكن البنية لا تستخرج الروابط المخفية ولا تتجاوز Cloudflare/CAPTCHA/Access Control (البند 19 من المواصفات: `PLAYBACK_UNAVAILABLE` وليس تجاوز). الأفلام الحديثة من هذه المواقع ستعرض "لا يوجد مصدر متاح" إلا إذا وُجد مصدر مباشر/شرعي (سيرفر Xtream خاص بك، Jellyfin، أو Backend رسمي).

---

## مرحلة D — التفاصيل الغنية + الواجهة السينمائية (2026-08-22)

### البيانات العامة المستخرجة من كل موقع (تم التحقق عملياً)
| المعلومة | TopCinemaa | EgyDead |
|---|---|---|
| العنوان / القصة / الصورة | og:title · og:description · og:image | og:title · og:description · og:image |
| السنة | من العنوان/الوصف (2026 ✓) | من العنوان (2026 ✓) |
| التقييم | ratingValue + ratingCount (8.0 / 200 ✓) | غير متوفر |
| الأنواع | من وصف الصفحة (أكشن • جريمة ✓) | من حقل "النوع" (إثارة أكشن جريمة ✓) |
| الطاقم | من "للنجم ..." (جيسون ستاثام ✓) | من "بطولة ..." (جيسون ستاثام، أنابيل ✓) |
| المواسم/الحلقات | مواسم بأرقام (`<span>الموسم</span>4`) + حلقات من نفس الصفحة (22 حلقة مرتبة 1..19 ✓) | صفحة `/season/slug-sXX/` (8 حلقات مرتبة 1..8 ✓) |
| الترتيب | رقمي (SeasonGroup.Builder يفرز) | رقمي |

### الجديد في الكود
- `MediaDetail` + `SeasonGroup` — نماذج موحّدة للتفاصيل والمواسم.
- `ContentProvider.details()` + `episodes()` — واجهة موحّدة.
- `TopCinemaaProvider`/`EgyDeadProvider` — تطبيق details + episodes مع ترتيب رقمي واقتطاع مكررات (embedScreen).
- `SiteMeta` — محلل ميتا مشترك (og/rating/genres/cast/year) بلا تكرار.
- `DetailActivity` — تصميم سينمائي: Header بخلفية Backdrop + تدرّج + أزرار، ثم القصة/الأنواع/الطاقم/المخرج/المواسم والحلقات. يعمل لـ TMDB ولمحتوى المواقع.
- `SiteEpisodesPanel` — مواسم/حلقات للمواقع ببطاقات مصغّرة + شريط تقدم المشاهدة.
- `SeriesEpisodesPanel` — أضيف شريط تقدم + انتقال تلقائي بين المواسم.
- `SearchActivity` — بحث موحّد (TMDB + المواقع) مع دمج وتفادي تكرار (Normalized Title + Year).
- `MainActivity` — Hero Banner + صفوف "أحدث الأفلام/المسلسلات" + شرائح الأنواع + متابعة المشاهدة.
- `RowPosterAdapter` — بطاقات أفقية مدمجة.
- `PlaybackController.prepare(..., resume)` + مفتاح "استئناف المشاهدة" في الإعدادات.
- Branding جديد: أيقونة Adaptive (LP + Play) + خلفية/توهج Splash جديدة + إعادة تحجيم mipmaps.
