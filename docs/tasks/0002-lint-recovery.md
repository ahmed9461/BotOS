# ملحق المهمة 0002 — تصحيح الموارد المتغيرة قبل تسليم المعاينة

2026-09-18. الحالة: planned. يتبع 0002-ci-recovery و0001-foundation. هذه الخطة تسبق تعديل Kotlin.

## الأدلة المقروءة

Run 35287754553 عند 65fbfdf946c36f25f124c9cbbabfa9d43df74fb4: تجهيز SDK وWrapper نجحا، core:model:test وcore:telegram:test وassembleDebug نُفذت بنجاح. فشل lint منع نشر APK. قرئت حزمة BotOS-checks-4 (artifact 10525415332): JUnit يحتوي 1+3 حالات ناجحة دون تخطي أو فشل؛ الحالة الجامعة للنواة تضم 37 assertion. تقرير lint يضم خطأين و9 تحذيرات، وليس فشل تجميع Kotlin.

الخطآن LocalContextGetResourceValueCall في BotOsApp.kt سطر57 و112: موارد إشعار الحدث ورد المعاينة غير متتبعة لتغير إعدادات الجهاز. يوجد تحذير compiler عن شرط selected != null دائمًا صحيح بعد isPreview.

## قراءات البداية

ذاكرة المشروع والبنية والقواعد والخطط قُرئت قبل استئناف0002. قرئت الآن BotOsApp.kt وWorkspaceScreen.kt وتقرير SARIF وJUnit XML الكامل. الفرع هو feat/android-foundation، PR#1، ولا تغيير للإصدارات أو تعطيل lint.

## خطة التنفيذ ومعيار القبول

1. استخدم LocalResources.current مع rememberUpdatedState داخل مجمع الأحداث الطويل؛ لا تلتقط Resources قديمة ولا تعيد تشغيل collector لمجرد إعادة تركيب الواجهة. اقرأ الرد الثابت عبر stringResource في التركيب ومرره للحدث.
2. احذف الشرط المكرر في مسار البوت غير الفارغ دون تغيير التنقل أو تمرير الأفعال. لا تغيير شكلي واسع ضمن إصلاح البناء.
3. أعد CI الكامل: checks، Python13، contrast14، JUnit4، lint، assemble، ثم اقرأ التقارير والمخرجات. لا baseline أو suppress أو continue-on-error لإخفاء المشكلة.
4. راجع التحذيرات ووثق غير المنجز بدقة. التحديثات المتاحة لـAGP/Gradle ليست إذنًا لتجاوز مجموعة التوافق. إعدادات النسخ الاحتياطي الحديثة والأيقونة أحادية اللون واختبارات تغيير اللغة على جهاز مهام متابعة قبل الإنتاج/TDLib؛ المعاينة لا تجمع جلسة حساب.
5. حدّث PROJECT_MEMORY وCHANGELOG وTESTING والخطط، وحدد نسخة APK المختبرة فقط إن اجتاز run كل بواباته.

## الملفات والتراجع

BotOsApp.kt وWorkspaceScreen.kt والوثائق أعلاه. التراجع بcommit عكسي لإصلاح المصدر، دون تغيير تاريخ الفرع. لا ادعاء نجاح جهاز أو FPS أو Telegram حي.

المراجع الأولية: https://android.googlesource.com/platform/frameworks/support/+/0624f640fd3a47edfcf8a070f609d278fb5eb41b/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidCompositionLocals.android.kt ، https://developer.android.com/develop/ui/compose/side-effects ، https://github.com/ahmed9461/BotOS/actions/runs/35287754553
