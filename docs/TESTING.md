# الاختبارات والأدلة

## النتائج المحلية — 2026-09-18

تم تجميع core/model مع CoreChecks.kt عبر kotlinc وتشغيل JAR: **37 core checks passed**. نجح فحص 14 زوجًا من ألوان النص/الخلفية الفعلية في الثيمين، جميعها >=4.5:1. هذه فحوص النواة والتباين، وليست اختبار Android.

## أوامر CI

```sh
python3 scripts/check_repo.py --base <base-commit>
python3 scripts/check_contrast.py
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug
```

Wrapper يُولد بالأداة الرسمية قبل الاستخدام؛ راجع BUILD.md. CoreChecks يسجل في Gradle كاختبار JUnit جامع، مع 3 اختبارات إضافية للمعاينة. لا تخلط 37 assertions بعدد حالات JUnit.

حالة Android/lint/JUnit Gradle وقت إضافة الكود: **بانتظار التشغيل**. سيضاف رقم run ونتيجته بعد قراءتهما فعلًا.

## حدود التحقق

لا دخول Telegram أو TDLib JNI في P1. لا FPS أو Baseline Profile مُنجز. لا فحص جهاز أو screenshots مثبتة بعد. نجاح build لا يثبت سلاسة الشاشة. المخرجات المتوقعة من CI: APK معاينة وchecksum وتقارير الاختبارات وWrapper رسمي.

## مراجعة جهاز مطلوبة

إضافة/تعديل/ترتيب/إزالة bookmark، إعادة التشغيل وحفظ التفضيلات، الثيمات وتقليل الحركة، رجوع المحرر، ضغطات متكررة، table/details/buttons، RTL وEnglish وخط200% وشاشة صغيرة وTalkBack. تُسجل الأدلة هنا قبل أي ادعاء اجتياز.
