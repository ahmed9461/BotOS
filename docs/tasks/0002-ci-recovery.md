# المهمة 0002 — استكمال التحقق من بناء Android

التاريخ: 2026-09-18. الحالة: validating. مهمة تابعة لبوابة التحقق في [0001](0001-foundation.md).

## نقطة البداية والأدلة

الفرع feat/android-foundation عند 0ac337e6507a04ab672190a7f3eb21ffe97cba69، PR #1. main يحتوي التخطيط، والفرع يحتوي التطبيق؛ يجب فحص الفروع لا افتراض أن main يمثل كل العمل.

قُرئت AGENTS والذاكرة والبنية والخارطة والقرارات والتصميم والأمان والقدرات وخطة0001، ثم فرق PR وworkflow وسجل job105412687739. كتبت خطة الاستئناف قبل السكربت في commit45a82b4a2e9ef58c093495ddacd9df717d2d6c42.

Run35284171101 فشل في Install Android platform: `sdkmanager: command not found` (127). نجح فحص المستودع و14 زوج تباين؛ Gradle tests/lint/APK لم تبدأ. https://github.com/ahmed9461/BotOS/actions/runs/35284171101

## الخطوات بالترتيب

- [x] 1. اكتشاف فرع التنفيذ وقراءة نقطة التوقف والسجل وتوثيق السبب قبل التعديل.
- [x] 2. فصل تجهيز SDK في سكربت يفحص الجذر والأداة صراحة، ولا يعتمد على PATH أو يخفي فشل الرخص/التنزيل.
- [x] 3. عشرة اختبارات محلية مع SDK مصطنع نجحت؛ تفاصيلها أدناه.
- [x] 4. وصل السكربت بالـworkflow مع تشغيل اختباراته قبل Android.
- [ ] 5. قراءة CI الجديد وإصلاح الفشل التالي بناءً على log، دون تعطيل بوابات الجودة.
- [ ] 6. توثيق النتائج النهائية والـAPK، تحديث ذاكرة وخطة0001 ودليل الاختبار، ومراجعة PR.

## التنفيذ والاختبارات

`bash -n scripts/install_android_sdk.sh`: ناجح محليًا.
`python3 -m unittest discover -s scripts/tests -v`: 10 اختبارات ناجحة محليًا في 2026-09-18، دون SDK حقيقي أو شبكة.

تغطي المسار ذي الفراغات وفصل package arguments، إصدارات أدوات SDK المثبتة، ANDROID_SDK_ROOT، الجذر المفقود/غير الموجود، منع أداة من SDK آخر في PATH، تعارض الجذور، فشل الرخص ومنع متابعة التثبيت، انتشار فشل التثبيت، وتصدير البيئة للخطوات التالية. هذه اختبارات سكربت، وليست دليل تجميع Android.

## الملفات والحدود

scripts/install_android_sdk.sh، scripts/tests/test_android_sdk.py، .github/workflows/android.yml، docs/PROJECT_MEMORY.md، docs/CHANGELOG.md، docs/BUILD.md، وهذه الخطة. لا تغيير إصدارات أو UI لحل PATH. لا نسخ للتطبيق أو تعديل خارج المستودع.

## القبول والتراجع

يجب أن تصل CI إلى test/lint/assemble فعليًا مع دليل؛ لا continue-on-error ولا دمج قبل المراجعة. إن غابت أدوات SDK نفسها يعلن مانع واضح بدل نجاح كاذب. revert commit الإصلاح يعيد حالة الفرع دون فقد أساس التطبيق.

مرجع SDK: https://developer.android.com/tools/sdkmanager
