# LATCHI PLAY — تقرير Phase 0 لمزوّد MovieBox

## الحالة

```text
PHASE 0: BLOCKED — AUTHORIZATION/API CONTRACT REQUIRED
```

تاريخ التقرير: 20 أغسطس 2026

---

## 1. الهدف الذي تم تقييمه

إثبات المسار الحقيقي التالي قبل حذف تطبيق LATCHI PLAY الحالي:

```text
Search
↓
Details
↓
Series / Seasons / Episodes
↓
Playback Resource
↓
Media3
↓
Video Playback
```

وفق Master Specification، لا يجوز بدء إعادة البناء أو حذف التطبيق القديم إذا لم يثبت هذا المسار بمصدر مشروع ومصرح.

---

## 2. ما تم إثباته من تحليل الكود العام

المشروع المرجعي:

```text
https://github.com/parthmax2/movie-box
```

هو عميل Python غير رسمي لخدمة خارجية، وليس خادم فيديو يملكه مشروع LATCHI PLAY.

البنية العامة الموجودة في الكود المرجعي تشمل:

- مجموعة API Hosts قابلة للتبديل.
- Homepage وSearch.
- Item details.
- Seasons وEpisodes.
- Playback/Play information.
- Resource/download information.
- Subtitles.
- Multiple resolutions.
- Runtime guest token.
- Request signing.
- Token refresh من Response headers.
- CDN request headers.
- Local browser proxy للتشغيل في الكمبيوتر.

نماذج البيانات العامة تشير إلى حقول من نوع:

```text
subjectId
subjectType
season
episode
resourceLink
sourceUrl
downloadUrl
resolution
codecName
duration
captions
```

هذا يثبت أن المشروع المرجعي يعرف آلية الوصول إلى Metadata وPlayback Resources، لكنه لا يثبت أن LATCHI PLAY مخول باستخدام الخدمة أو أن الـAPI رسمي ومسموح لتطبيق طرف ثالث.

---

## 3. سبب إيقاف POC

لم يتم تقديم أي من الآتي:

- وثائق API رسمية.
- موافقة على إنشاء Third-party Android client.
- Test tenant أو Test account مصرح.
- Credentials اختبار عبر قناة أسرار آمنة.
- شروط استعمال تسمح بعرض المحتوى في تطبيق LATCHI PLAY.
- توثيق Rate limits.
- توثيق صلاحية Playback URLs.
- توثيق Headers المسموح إرسالها من تطبيق طرف ثالث.

كما أن البروتوكول العام في المشروع المرجعي يعتمد على:

```text
Signed request headers
Runtime bearer/guest token
Token bootstrap
Token refresh
Private service hosts
```

تشغيل POC حقيقي دون إثبات حق الوصول سيعني استعمال Private/Unofficial API لاختبار محتوى لم يقدم صاحب المشروع حق استعماله. هذا يتعارض مع الأقسام 8 و10 و85 من Master Specification نفسها.

---

## 4. ما لم يتم فعله

لم يتم:

- نسخ أسرار أو Tokens من المشروع المرجعي.
- تشغيل Private API للحصول على فيلم.
- استخراج رابط Playback حقيقي.
- تجاوز Authentication.
- تجاوز DRM أو CAPTCHA أو Paywall.
- استعمال Cookie شخصية.
- استعمال Mock video كدليل نجاح.
- حذف تطبيق LATCHI PLAY الحالي.
- حذف Production signing configuration.
- تغيير Package.
- تغيير Version Name أو Version Code.
- تشغيل Codemagic.
- إنشاء GitHub Release.
- رفع APK أو AAB.

---

## 5. لماذا لم يتم حذف التطبيق القديم؟

Master Specification ينص على:

```text
ابدأ بـPhase 0
أثبت Search → Details → Episodes → Playback Resource → Media3
إذا فشل، توقف وأبلغ عن المشكلة
```

وبالتالي حذف التطبيق القديم قبل رفع Blocker سيكون تصرفًا مدمرًا وغير متوافق مع المواصفات.

النسخة الحالية ما زالت تحافظ على:

```text
Package: com.latchi.play
Version Name: 3.1.0
Version Code: 7
Production signing: Codemagic secure group
```

---

## 6. المعلومات المطلوبة لرفع الحظر

يلزم أن يقدم المحلل أو مالك المصدر تقريرًا منظمًا يحتوي على:

### Authorization Status

واحد من:

```text
Official API
Authorized partner API
Written permission
User-owned licensed backend
```

قيمة `Unofficial/Unknown` لا تكفي لبناء تطبيق Production.

### API Contract

لكل endpoint:

```text
Method
Path
Parameters
Required header names
Request body schema
Response schema
Pagination
Error codes
Rate limits
```

### Auth Contract

```text
Bootstrap endpoint
Token type
Token TTL
Refresh behavior
Permitted client type
Whether a client secret is required
Whether the secret may legally exist on-device
```

### Playback Contract

```text
Movie playback request
Episode playback request
Media URL field
Media type: MP4/HLS/DASH
Required CDN header names
URL expiration
Range support
Redirect behavior
Region restrictions
DRM requirements
```

### Subtitles/Audio

```text
Subtitle endpoint
Formats
Languages
Delay
Audio/dub tracks
```

### Redacted Samples

عينات JSON منزوعة الأسرار، مثل:

```json
{
  "contentId": "TEST_CONTENT_ID",
  "type": "movie",
  "resources": [
    {
      "quality": "720p",
      "mediaType": "dash",
      "url": "https://licensed-cdn.example/path/manifest.mpd?token=REDACTED",
      "expiresAt": 1780000000,
      "requiredHeaderNames": ["Example-Header"]
    }
  ]
}
```

---

## 7. الاختبار المطلوب من المحلل

على المحلل إثبات المسار على محتوى Test/Authorized:

```text
1. Bootstrap succeeds
2. Search returns test item
3. Details return valid metadata
4. Series returns seasons
5. Season returns episodes
6. Playback endpoint returns a permitted resource
7. URL plays in Android Media3
8. Seek works
9. Expired URL can be refreshed
10. Subtitle works
11. Host failover behavior is documented
```

يجب تقديم:

```text
Pass/Fail
Elapsed time
HTTP status
Media type
URL TTL
Required header names
Device/Android version
```

من دون إرسال القيم السرية نفسها.

---

## 8. القرار المعماري بعد رفع الحظر

إذا كان API يسمح بالاتصال من Android دون Secret خاص:

```text
LATCHI PLAY
↓
MovieBoxProvider (Java)
↓
Authorized API
↓
PlaybackSource
↓
Media3
```

إذا كان API يحتاج Secret لا يجوز نشره داخل APK:

```text
LATCHI PLAY
↓
LATCHI Backend
↓
Authorized API
↓
Playback metadata
↓
LATCHI PLAY
↓
CDN directly
↓
Media3
```

لن يتم وضع Server-side secret داخل Java أو APK.

---

## 9. الملفات التشغيلية التي يجب الحفاظ عليها عند بدء Rebuild

بعد نجاح Phase 0 فقط، يجب الاحتفاظ على الأقل بـ:

```text
Package: com.latchi.play
Gradle wrapper
Codemagic configuration
Production signing variable names
Update signing identity
Git history
Network security baseline
```

يمكن إعادة بناء كود التطبيق والواجهات والـAssets، لكن لا يجوز تغيير Signing identity إذا أردنا تحديث النسخ المثبتة.

---

## 10. شرط الاستئناف

يمكن بدء `DELETE OLD APPLICATION → CLEAN REBUILD` فقط بعد استلام تقرير محلل تكون نتيجته:

```text
AUTHORIZATION: CONFIRMED
API CONTRACT: COMPLETE
PLAYBACK RESOURCE: VERIFIED
MEDIA3 TEST: PASS
DRM BYPASS: NOT REQUIRED
PRIVATE SECRET IN APK: NOT REQUIRED
```

إذا كان أحد الشروط غير متحقق، يجب تصميم Backend مصرح أو اختيار مزوّد آخر.

---

# FINAL PHASE REPORT

## PHASE

```text
Phase 0 — MovieBox Source Proof of Concept
```

## FILES CREATED

```text
docs/MOVIEBOX_PHASE0_REPORT_AR.md
```

## FILES MODIFIED

```text
None
```

## FILES REMOVED

```text
None
```

## ARCHITECTURE

لم تتغير. تم الحفاظ على التطبيق الحالي إلى أن ينجح Proof of Concept المصرح.

## FEATURES

لا توجد Features Production جديدة؛ هذه مرحلة Gate/Validation.

## SOURCE

```text
MovieBox Provider status: UNOFFICIAL / AUTHORIZATION NOT PROVIDED
API structure: visible in public code
Authorized playback: NOT VERIFIED
```

## PLAYBACK

```text
Media3 exists in current project
MovieBox real authorized PlaybackSource: NOT VERIFIED
```

## TV

```text
Current Android TV support preserved
No MovieBox TV flow implemented
```

## PERMISSIONS

لم تتغير.

## TESTED

- Repository identity inspected.
- Package/version/signing configuration preserved.
- Public reference architecture reviewed.
- API/Auth/Playback requirements identified.
- Secret exposure risk identified.

## NOT TESTED

- Real MovieBox authentication.
- Search against Private API.
- Real Playback Resource.
- Media3 with MovieBox resource.
- Subtitles/audio/quality.
- Host failover at runtime.

## KNOWN ISSUES

- لا توجد وثائق API رسمية أو موافقة مقدمة.
- API غير الرسمي قابل للتغيير.
- Request signing قد يحتاج Secret غير مناسب للـAPK.
- حق استخدام المحتوى غير مثبت.

## BUILD

```text
NOT RUN
```

## RELEASE

```text
NOT CREATED
```
