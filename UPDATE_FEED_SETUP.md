# ربط إشعارات تحديث Focus Study

التطبيق يقرأ ملف JSON صغيرًا عبر HTTPS، ويقارن `versionCode` المثبت بأحدث إصدار. عند وجود إصدار أحدث، يعرض تنبيهًا مرة واحدة لهذا الإصدار، ويفتح صفحة المتجر عند طلب التحديث.

## ملف latest.json

استضف سجل إصدار عامًّا عبر HTTPS، أو استخدم موقع Vercel. يحتوي السجل على رابط صفحة التطبيق في المتجر، ولا يحتوي على رابط APK للتثبيت المباشر. هذه صيغة الاستجابة التي يقرأها التطبيق:

```json
{
  "packageName": "com.focusstudy.poc",
  "versionCode": 12,
  "versionName": "0.11.0",
  "storeUrl": "https://qubixstore.vercel.app/focus-study-updates",
  "apkUrl": "https://github.com/OWNER/REPO/releases/download/focus-v0.11.0/Focus-Study.apk",
  "releasePageUrl": "https://update-site-sigma.vercel.app/#download",
  "releaseNotes": "تحسينات جديدة وإصلاحات للأخطاء."
}
```

`packageName` يجب أن يطابق الحزمة، ويجب أن يطابق `versionCode` الإصدار المنشور ويزداد مع كل إصدار. `storeUrl` يفتح صفحة QUBIXStore، بينما `apkUrl` هو ملف APK الذي يظهر في صفحة المتجر. يظل إشعار التطبيق موجّهًا إلى صفحة المتجر.

## ربط الرابط بالتطبيق

مرّر عنوان ملف JSON عند بناء APK. عنوان المشروع الحالي:

```powershell
.\gradlew.bat assembleRelease -PfocusStudyUpdateFeedUrl="https://update-site-sigma.vercel.app/api/latest"
```

إذا كان العنوان غير مضبوط، ستعرض شاشة «تحديثات التطبيق» أن المصدر غير مربوط. بعد ضبطه، يفحص التطبيق عند فتحه ومرة يوميًا، ويرسل إشعارًا عند اكتشاف إصدار أعلى. عند الضغط على التحديث يفتح صفحة التطبيق في المتجر.

## نشر إصدار من لوحة الإدارة

افتح [لوحة إدارة التحديثات](https://update-site-sigma.vercel.app/admin/)، وسجّل الدخول، واربط رابط المتجر. بعد نشر الإصدار في المتجر، اكتب اسمه ورقمه وملاحظاته في اللوحة وانشر إشعار التحديث. يجب أن يطابق توقيع المتجر النسخة المثبتة حتى يتم التحديث فوقها وتبقى بيانات الطالب. لا تشارك بيانات دخول الإدارة.

للنشر الآلي، ادفع وسمًا مثل `focus-v0.12.0` بعد رفع `versionName` و`versionCode` في Gradle. يرفع GitHub Actions APK إلى GitHub Release ثم يرسل بياناته إلى `https://update-site-sigma.vercel.app/api/admin/publish-ci`. اضبط أسرار `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, و`FOCUS_RELEASE_SYNC_TOKEN` في مستودع التطبيق، واجعل `FOCUS_RELEASE_SYNC_TOKEN` و`BLOB_READ_WRITE_TOKEN` متاحين لمشروع Vercel الخاص بموجز التحديثات. لا ترفع ملف التوقيع أو الأسرار إلى Git.

