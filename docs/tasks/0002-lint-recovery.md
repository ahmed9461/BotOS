# ملحق المهمة 0002 — تصحيح الموارد المتغيرة

2026-09-18. الحالة: validating. الخطة كتبت في commit e820da62e58ec2640c21d167a206591a17fb5687 قبل تعديل المصدر. يتبع 0002-ci-recovery و0001-foundation.

## الدليل

Run 35287754553 عند 65fbfdf946c36f25f124c9cbbabfa9d43df74fb4: SDK وWrapper وcore:model:test وcore:telegram:test وassembleDebug نجحت. lint فشل بخطأين و9 تحذيرات، فلم يُنشر APK. artifact 10525415332 قُرئ كاملًا: JUnit 1+3 حالات ناجحة دون تخطي؛ حالة النواة الجامعة تضم 37 assertion. الخطآن LocalContextGetResourceValueCall في BotOsApp.kt سطر57 و112. تحذير compiler إضافي لشرط selected != null المكرر.

## التقدم

- [x] قراءة المصدر وتقرير SARIF وتقارير JUnit ومراجعة مرجع LocalResources وrememberUpdatedState.
- [x] استخدام LocalResources.current مع rememberUpdatedState في مجمع الأحداث، وstringResource لرد المعاينة قبل callback. لا قراءة نصوص من LocalContext.
- [x] توضيح مسار selected == null/else وإزالة الشرط المكرر دون تغيير السلوك.
- [ ] إعادة CI وقراءة lint/JUnit/APK؛ لا نجاح مفترض قبل النتيجة.
- [ ] تحديث نتائج TESTING والذاكرة والخطط وPR، ثم تسليم معاينة محددة المصدر إذا اجتازت جميع البوابات.

## التحذيرات المتبقية والمتابعة

لم نعطل lint ولم نضف baseline أو suppress. التحذيرات التسعة في run4: 3 إشعارات تحديث أدوات البناء، localeConfig على API33+، قواعد نقل البيانات الحديثة، مجلد الأيقونة v26 المكرر، نص غير مستخدم، monochrome icon، واقتراح toUri. لا تغير مجموعة التوافق تلقائيًا. قواعد backup/نقل البيانات واختبار الأيقونة واللغة على جهاز مطلوبة قبل الإنتاج أو حفظ جلسة TDLib؛ المعاينة لا تجمع جلسة.

اختبارات إعادة إنشاء Activity وتغير اللغة غير منفذة على جهاز حتى الآن؛ إصلاح المصدر ونجاح lint لاحقًا لا يعوضان ذلك. لا FPS أو اتصال Telegram حي.

الملفات: BotOsApp.kt وWorkspaceScreen.kt ووثائق الذاكرة/السجل/الاختبار. التراجع بcommit عكسي دون محو التاريخ.

المراجع: https://android.googlesource.com/platform/frameworks/support/+/0624f640fd3a47edfcf8a070f609d278fb5eb41b/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidCompositionLocals.android.kt ، https://developer.android.com/develop/ui/compose/side-effects ، https://github.com/ahmed9461/BotOS/actions/runs/35287754553
