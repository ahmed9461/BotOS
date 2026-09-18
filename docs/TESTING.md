# الاختبارات والأدلة

آخر مراجعة: 2026-09-18. آخر مصدر P2b مقروء: `e8f86b4cfae53fd987eaa8454c19c2d133ad34b5`. تهيئة بيانات Telegram تحققت في runner موثوق، لكن التطبيق لم يسجل الدخول ولم يرسل إلى بوت حي بعد. بوابة UI للمصدر الحالي فشلت بسبب Launcher ANR قبل تركيز BotOS، لذلك لا تعد نسخة الربط مكتملة.

## تهيئة بيانات Telegram الموثوقة

[التشغيل 35387386142](https://github.com/ahmed9461/BotOS/actions/runs/35387386142) على e8f86b4c نجح. شغلت المهمة السرية 24 اختبار Python، ثم حقنت زوج BOTOS_TELEGRAM_API_ID/BOTOS_TELEGRAM_API_HASH من GitHub Secrets في env للخطوة المحدودة فقط. تحقق الشكل نجح، وشُغّل `:app:generateDebugBuildConfig`، ثم تحقق داخل runner أن القيم المولدة تطابق المدخلات. لم تُطبع القيم أو أطوالها أو بصماتها؛ cache وbuild scan كانا معطلين، وأزيل `app/build` بعد الفحص ولم يرفع APK أو BuildConfig مولد. الخرج المنطقي فقط: configured، وTelegram authentication بقي not_tested.

هذا يثبت وصول بيانات التطبيق وصحة التوليد، لا قبول Telegram لها في جلسة ولا تسجيل دخول صاحب الحساب.

## بوابة P2b الحالية والفشل المقروء

[Android 35387390277](https://github.com/ahmed9461/BotOS/actions/runs/35387390277) نجح في فحوص المستودع والتباين والعقد، و24 Python، وJVM/lint/assemble، ثم فشلت حالات UI الثلاث جميعًا في `UiRegressionTest.windowReady` بعد 10 ثوانٍ.

[native 35387390278](https://github.com/ahmed9461/BotOS/actions/runs/35387390278) نجح في بناء arm64-v8a وx86_64 والعقد وJVM و8 حالات JNI/Keystore، ثم فشلت حالات UI الثلاث عند حاجز التركيز نفسه. نُزلت حزمة `BotOS-runtime-checks-12` وفُتحت أدلتها:

- `failure-screen.png`: dialog نظام `Pixel Launcher isn't responding`.
- `failure-window.txt`: last ANR لتطبيق `com.google.android.apps.nexuslauncher/.NexusLauncherActivity`، والسبب `Input dispatching timed out (Application does not have a focused window)`، وcurrent focus هو dialog الـANR.
- `failure-power.txt`: الجهاز Awake، فلا يفسر الفشل بنوم الشاشة.
- XML/logs: الاختبارات تصل إلى `waiting_for_window_focus` ولا تبدأ شروط المظهر/IME/الحفظ قبل انتهاء المهلة.

إذن الفشل الحالي بيئي قبل اختبار BotOS. خطة `tasks/0005-emulator-focus.md` سبقت تعديل harness: لا KEYCODE_MENU، حل HOME ديناميكيًا وإيقافه على المحاكي المؤقت، ووضع Settings في المقدمة والتحقق من resumed activity. لا تغيير في `windowReady=10s` أو انتظار IME=15s أو شرط gap=-2..12dp. نجاح التعديل لم يُفترض قبل CI.

## أحدث بوابة مكتملة للمحرك والمنسق

تشغيل المحرك `35384252317` على c016f00a نجح: 36 JVM، منها 15 حالة AccountCoordinator، و8 JNI/Keystore و3 UI بلا فشل أو تخطي؛ lint المحرك 0/0، وgap=6.095238dp. هذا يثبت المنسق مع بدائل مصطنعة والمحرك على الجهاز، وليس دخول حساب.

## بوابة P2a المكتملة

[تشغيل المحرك 35380280277](https://github.com/ahmed9461/BotOS/actions/runs/35380280277) على e9b45c60 نجح كاملًا. نُزلت حزمة `BotOS-runtime-checks-8` رقم `10562130090` وطابقت بصمتها `35b9fb907820bc726e949b8dab0b4a5c940bda0f8e7a4b6462ee5fee9c57e9f4`.

| الفحص | النتيجة المقروءة |
|---|---|
| JVM | 21 حالة: CoreTest1، PreviewTest3، AuthorizationTest8، TdTransportTest9؛ لا فشل/خطأ/تخطي |
| JNI وKeystore على محاكي API35 | 8 حالات، لا فشل/خطأ/تخطي |
| واجهة التطبيق على المحاكي | 3 حالات، لا فشل/خطأ/تخطي؛ المدة44.988 ثانية |
| lint وحدة core:tdlib | صفر أخطاء وصفر تحذيرات |
| native-runtime.txt | version1.8.67، commit مطابق للمصدر المثبت، العربية/emoji ناجحة، waitTdlibParameters، accountUsed=false |
| اللقطات | سبع صور فعلية فُتحت للمراجعة: المظهر بثلاث حالاته، المساحة، المكتبة، المحرر، والكيبورد |
| الكيبورد | gap6.095238dp؛ rootHeight2400، imeBottom883، composerBottom1501؛ ضمن شرط -2..12dp |
| AAR | يحتوي libtdjsonjava.so لكل من arm64-v8a وx86_64؛ ليس APK متصلًا |

[تشغيل Android المستقل 35380280304](https://github.com/ahmed9461/BotOS/actions/runs/35380280304) نجح أيضًا في tests/lint/assemble واختبارات الواجهة وجمع الأدلة وإخراج معاينة0.2. لا يثبت ذلك إرسال رسالة حقيقية من داخل التطبيق.

## إصلاح تعليق سابق دون تخفيف الشروط

التشغيل35377793103 على89351f43 اجتاز21JVM و8JNI/Keystore، لكن UI توقف عند0/3 حتى مهلة8دقائق. خطة8d25d289 سبقت تصحيحe9b45c60: إزالة runOnIdle المتداخل مع getter النشاط، وقراءة snapshot النافذة مرة واحدة عبرActivityScenario من خيط الاختبار. لم تتغير الست تبديلات أو ظهور الكيبورد الفعلي أو شرط المسافة أو حفظ المدخلات/الإرسال. نجاح التشغيلين اللاحقين لم يكن إعادة عشوائية للفشل.

## ما لم يثبت بعد

لا تسجيل دخول Telegram أو بوت حي من داخل app. لا اختبار حساب/OTP/كلمة مرور في CI. لا تحقق userTypeBot حي أو إرسال/Callback/Reply حي بعد. لا قياسFPS أو BaselineProfiles أو TalkBack شامل أو مصفوفة أجهزة أو runtime16KB أو اختبار شبكة ضعيفة. محاذاةELF ليست اختبار تشغيل16KB. توقيع الإنتاج الدائم وقواعد النسخ تحتاج اكتمالًا قبل إصدار متصل عام. Rich/media/streaming/MiniApps ليست مكتملة.

## أوامر التحقق

```sh
python3 scripts/check_repo.py --base <base-commit>
python3 scripts/check_contrast.py
python3 scripts/check_ui_contract.py
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_tdlib_contract.py
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --no-daemon :core:tdlib:lintDebug :core:tdlib:assembleDebug :core:tdlib:assembleDebugAndroidTest
bash scripts/run_ui_tests.sh :core:tdlib:connectedDebugAndroidTest
python3 scripts/check_device_evidence.py
python3 scripts/check_runtime_evidence.py
```

المكتبات الأصلية يجب أن تكون من نفس تشغيل البناء المثبت. Wrapper يولد رسميًا وفقBUILD. البيئة المحلية بلاAndroidSDK؛ اختبارات الجهاز المعلنة تنفذ عبرActions، لا في الحاوية المحلية.
