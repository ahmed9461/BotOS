# ملحق المهمة0002 — فحص الحزم المنشورة قبل أي تغيير إصدار

2026-09-18، in_progress. يتبع tasks/0002-ci-recovery.md. خطة التحقيق قبل التنفيذ: commit0ab0905a691b0e4fc897d6e5a369d15c34f53875.

## نتائج التحقيق المثبتة

Run35287534204 عند81f0edd422417348567efb515c9739d755567112. artifact10524798195 (BotOS-checks-3)، قُرئ ملفا diagnostics/dependencies.json وsdk-packages.txt فعليًا.

Google Maven/Maven Central يؤكدان نشر جميع الإصدارات الثمانية المثبتة: AGP9.3.1، Kotlin2.4.20، BOM2026.09.00، Activity1.13.0، Lifecycle2.11.0، Nav3 1.1.7، DataStore1.2.1، Coroutines1.11.0. لا تغيير للإصدارات.

السبب الدقيق لتعطل SDK: الاسم المنشور هو **platforms;android-37.0** وليس platforms;android-37. تظهر37.0 revision2 مثبتة ومتاحة، وكذلك build-tools36.0.0 و37.0.0. إذن لم تكن المشكلة عدم نشر Android17 ولا حاجة لترقية cmdline-tools لحلها. الأدوات12.0 قرأت القائمة بنجاح. لا rename لملفات SDK أو symlink لإخفاء اختلاف الهوية.

## خطة التصحيح بعد التحقيق — قبل تعديل workflow

1. غيّر معرف حزمة التثبيت فقط إلى platforms;android-37.0. أبق compileSdk/targetSdk37 (مستوى API) والمكتبات المختارة كما هي حتى اختبار AGP الفعلي.
2. حدّث أمر BUILD والذاكرة والسجل مع الفرق بين package identifier ومستوى API، وأرفق هذا الدليل.
3. أعد CI وراجع توليد Wrapper ثم Gradle tests/lint/assemble. عند خطأ جديد سجل log قبل تغييره.
4. بعد نجاح البناء سجل الاختبارات وAPK/التوقيع التجريبي والـchecksum وحدود المعاينة، ولا تعتبر ذلك اختبار جهاز أو اتصال Telegram.

## ما اجتاز

repo checks، تباين14، وPython tests13 محليًا ومرحلة checks فيCI؛ XML tests تختبر parser ولا تثبت شبكة، ودليل الشبكة هو artifact المذكور. المراحل اللاحقة للبناء لم تنفذ بعد.

المصادر: https://github.com/ahmed9461/BotOS/actions/runs/35287534204 ، https://developer.android.com/tools/sdkmanager ، https://developer.android.com/about/versions/17/setup-sdk . مصادر Maven الدقيقة محفوظة في artifact.
