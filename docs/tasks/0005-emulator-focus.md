# ملحق 0005 — تثبيت بيئة UI بعد تشخيص ANR للـ Launcher

التاريخ: 2026-09-18. الحالة: replanned من دليل التشغيل 13 قبل التصحيح الثاني. الفرع `feat/telegram-connection` / PR4. هذه الشريحة لا تغير واجهة الإنتاج أو التصميم أو الأيقونة أو مهلة انتظار تركيز نافذة التطبيق أو شرط فجوة الكيبورد.

## الأدلة المقروءة

- التشغيل الموثوق `35387386142` على `e8f86b4c` نجح في تهيئة بيانات Telegram: وصل الزوج كاملًا إلى المهمة السرية، اجتاز تحقق الصيغة، وتطابق `BuildConfig` المولد مع المدخلات داخل runner دون طباعة القيم أو رفع المصدر المولد. النتيجة المنطقية configured، والمصادقة `not_tested`.
- Android `35387390277` وnative `35387390278` نجحا في الفحوص/JVM/lint/build، وnative نجح في 8 JNI/Keystore، ثم فشلت حالات UI الثلاث عند `windowReady` بعد 10 ثوانٍ. حزمة التشغيل 12 أظهرت dialog `Pixel Launcher isn't responding` وANR لـNexusLauncher بينما الجهاز Awake.
- الخطة الأولى `072e5ddc` سبقت تعديل `b64d06b1`: أزيل فعل المفتاح 82، وأضيفت محاولة إيقاف HOME ووضع Settings في المقدمة دون تغيير assertions.
- Android `35388722974` على `b64d06b1` توقف مبكرًا لأن اختبارًا ساكنًا منع كلمة `KEYCODE_MENU` حتى داخل تعليق توثيقي؛ هذا false positive في الاختبار نفسه، لا استخدام فعلي للمفتاح.
- native `35388722988` وصل إلى preflight بعد نجاح المعماريتين والتجميع/JVM/lint. الحزمة `BotOS-runtime-checks-13` وبصمتها المسجلة في Actions `fed3214520695b004d3f3120edb1e603cb481f2402b690c3957ecf2cddff6584` قُرئت فعليًا.
- `preflight.txt` يثبت أن `resolve-activity MAIN+HOME` أعاد `com.google.android.googlesdksetup`، لا NexusLauncher، رغم أن `am start -W -a android.settings.SETTINGS` أعاد `Status: ok` و`Activity: com.android.settings/.homepage.SettingsHomepageActivity`.
- بعد ذلك مباشرة `preflight-activity.txt` و`preflight-window.txt` يثبتان أن NexusLauncher هو `topResumedActivity` و`mCurrentFocus`، وأنه مرسوم ومرئي. `lastanr` يقول صراحة `<no ANR has occurred since boot>`، والصورة تُظهر Launcher طبيعيًا بلا dialog. إذًا حذف المفتاح 82 أزال العطل المرصود في هذه المحاولة، بينما محاولة حل HOME عبر Package Manager كانت خاطئة لأن Setup wrapper يتصدر intent أثناء حالة الجهاز هذه، ومحاولة فرض Settings لم تضف قيمة.

## الخطة المنقحة الملزمة

1. أبقِ إزالة `KEYCODE_MENU`/المفتاح 82 من التنفيذ الفعلي؛ لا تعيده بأي اسم. أصلح اختبار العقد الساكن ليمنع أوامر الإدخال الفعلية فقط (`input keyevent 82` أو `input keyevent KEYCODE_MENU`) ولا يفشل بسبب تعليق يذكر السبب التاريخي.
2. أزل حل HOME الديناميكي و`force-stop` ومحاولة تثبيت Settings؛ الدليل يثبت أن resolver لا يعكس الـLauncher الحقيقي في هذه البيئة وأن Settings تعود إلى HOME بلا فائدة.
3. بعد wake + `wm dismiss-keyguard` على المحاكي غير الآمن، نفّذ preflight محدودًا يراقب حالة النظام فقط: يجب أن توجد نافذة مركزة ونشاط resumed، ويجب ألا يحتوي `dumpsys window lastanr` على ANR حقيقي. لا يشترط اسم حزمة معينة ولا يغير أي تطبيق نظام.
4. احتفظ بتشخيص preflight (activity/window/screenshot) عند الفشل. هذه مهلة تجهيز مستقلة ولا تغير `UiRegressionTest.windowReady=10s` أو انتظار IME=15s أو gap=-2..12dp.
5. لا تغير `UiRegressionTest` أو UI الإنتاج أو الحركة/الثيم/Insets. لا skip ولا retry عشوائي ولا زيادة مهل للوصول إلى نجاح.
6. شغّل بوابتي Android وnative على التعديل الجديد. النجاح يتطلب نفس حالات UI الثلاث و8 JNI/Keystore وJVM/lint/build، ثم قراءة XML واللقطات والقياس. إذا فشل، اقرأ الدليل الجديد قبل أي تعديل آخر.
7. بعد نجاح البوابتين، أغلق ملحق تهيئة البناء بالأدلة وانتقل فقط عندها إلى شاشة الحساب الحقيقية وربطها بمنسق واحد وTDLib.

## معايير القبول

- لا ANR نظام يمنع نشاط الاختبار من أخذ التركيز، ولا تلاعب بتطبيق HOME لتجاوز الفحص.
- تبقى الحالات الثلاث نفسها والمهل والـassertions كما هي وتنجح على المحاكي الحقيقي.
- تبقى 8 حالات JNI/Keystore واختبارات JVM/lint/build ناجحة.
- لا تغيير في Porcelain/Graphite/Iris أو launcher assets أو DataStore أو منطق الإنتاج.
- لا أسرار أو `BuildConfig` مولد أو بيانات حساب في artifacts التشخيص.
