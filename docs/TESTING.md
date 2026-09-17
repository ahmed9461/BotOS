# الاختبارات والأدلة

## 2026-09-18 — أول تجميع Android

Run35287754553 عند commit65fbfdf946c36f25f124c9cbbabfa9d43df74fb4. قرئت الحزمة BotOS-checks-4 (artifact10525415332)، SHA256 للـZIP:
`d7d9af47c9579f423022ef8f830c600090814111032984769a606504fee3ef86`.

نجح تجهيز SDK وWrapper وتجميع التطبيق وassembleDebug. تقارير JUnit: CoreTest حالة واحدة وPreviewTest ثلاث حالات، failures=0/errors=0/skipped=0. الحالة الجامعة للنواة تنفذ37 assertion؛ لا تخلط assertions بعدد اختبارات JUnit.

نجحت كذلك13 Python tests (10 محاكاة SDK و3 تحليل metadata)، وفحص14 زوج لون >=4.5:1، وفحص المستودع/XML/تزامن التوثيق. **الـrun ككل فشل لأن lint أبلغ خطأين و9 تحذيرات، لذلك لم يُنشر APK.**

بعد قراءة التقرير كتبت خطة إصلاح قبل Kotlin: resources متتبعة للتكوين بدل LocalContext.getString، وإزالة شرط مكرر. نتيجة إعادة CI بعد الإصلاح ما زالت بانتظار القراءة؛ لا نجاح افتراضي.

## الأوامر

```sh
python3 scripts/check_repo.py --base <base-commit>
python3 scripts/check_contrast.py
bash -n scripts/install_android_sdk.sh
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug
```

Wrapper يولد رسميًا قبل استخدامه؛ راجع BUILD.md. تشغيل فحص النشر لا يعني توافق التطبيق، واختبار SDK المصطنع لا يعني تثبيت Android.

## حدود التحقق والمراجعة المطلوبة

لا جهاز أو محاكي أو screenshots أو FPS/Baseline Profile مختبر حتى الآن. لا TDLib/login/media/streaming حقيقي فيP1. يلزم اختبار إضافة/تعديل/ترتيب/إزالة bookmark وحفظ الإعدادات وإعادة إنشاء Activity وتغيير اللغة أثناء إشعار، والثيمات وتقليل الحركة ورجوع المحرر والضغط المتكرر والجداول والأزرار، RTL/English وخط200% وشاشة صغيرة وTalkBack. نجاح تجميع APK لا يثبت سلاسة الواجهة.

التحذيرات التسعة مصنفة في tasks/0002-lint-recovery.md. لا جلسات حساسة في هذه المعاينة؛ قواعد النسخ الاحتياطي/نقل البيانات يجب إتمامها قبلP2.

المصدر: https://github.com/ahmed9461/BotOS/actions/runs/35287754553
