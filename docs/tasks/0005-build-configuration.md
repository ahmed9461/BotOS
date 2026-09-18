# ملحق 0005 — تهيئة بيانات التطبيق من أسرار البناء

2026-09-18. الحالة: configuration validated؛ بوابة UI التالية قيد إصلاح بيئة المحاكي. نقطة البداية c016f00a على feat/telegram-connection / PR4. أكد المالك إضافة BOTOS_TELEGRAM_API_ID وBOTOS_TELEGRAM_API_HASH في GitHub Secrets. لا تُطلب القيم منه في المحادثة ولا ندّعي قبولها من Telegram.

## القراءة والأدلة

قُرئت AGENTS والذاكرة والبنية والخارطة والقرارات والأمان والقدرات والتصميم وخطط 0005، وملفات Gradle وCI ومنسق الحساب واختبارات الجهاز.

تشغيل المحرك 35384252317 على c016f00a نجح. حزمة 10563976822 أثبتت 36 JVM (منها 15 AccountCoordinator) و8 JNI/Keystore و3 UI بلا فشل أو تخطي؛ lint المحرك 0/0، والجهاز دون حساب؛ gap=6.095238dp. تشغيل Android المستقل 35384252163 فشل في الحالات الثلاث عند windowReady، لذلك لم يُخف الفشل بنجاح التشغيل الآخر.

بعد تأكيد المالك للأسرار، التشغيل الموثوق `35387386142` على `e8f86b4c` اجتاز 24 اختبار Python وتحقق التهيئة الحقيقي. وصل الزوج كاملًا إلى خطوة سرية مع cache معطل وbuild scan معطل، نجح تحقق الصيغة، شُغّل `:app:generateDebugBuildConfig`، ثم تحقق داخل runner أن الحقول المولدة تطابق المدخلات دون طباعة القيم أو طولها أو بصمتها. أزيل `app/build` بعد التحقق ولم يُرفع APK أو BuildConfig مولد. الخرج المنطقي: configured، والمصادقة not_tested. هذا يغلق سؤال وصول الأسرار وصحة التوليد، ولا يثبت تسجيل دخول Telegram.

Android `35387390277` وnative `35387390278` على e8f86b4c نجحا في الفحوص/JVM/lint/build، وnative نجح كذلك في 8 JNI/Keystore، لكن كلاهما فشل في حالات UI الثلاث عند `windowReady` بعد 10 ثوانٍ. حزمة native `BotOS-runtime-checks-12` قُرئت ولقطة الفشل تعرض `Pixel Launcher isn't responding`. `dumpsys window lastanr` يحدد `com.google.android.apps.nexuslauncher/.NexusLauncherActivity` وسبب Input dispatching timeout بسبب عدم وجود focused window، بينما الجهاز Awake. لذلك انتقلت معالجة الفشل إلى [خطة 0005-emulator-focus](0005-emulator-focus.md) بدل زيادة المهلة أو خفض شروط الاختبار.

## التنفيذ بالترتيب

1. ✅ تأمين حالة المحاكي المؤقت وجمع حالة النافذة والطاقة واللقطة عند الفشل، دون تغيير واجهة الإنتاج أو شروط UI.
2. ✅ إضافة تحقق إعدادات لا يطبع قيمة أو طولًا أو بصمة سر: يقبل غياب القيمتين للمعاينة، ويرفض القيمة الجزئية أو ID غير موجب خارج int32 أو hash غير مطابق لصيغة 32 خانة hex. وضع require يرفض الغياب. اختبارات بيانات مصطنعة فقط.
3. ✅ تهيئة BuildConfig عبر public variant API الخاص بـAGP 9.3 مع buildConfig=true. اختيار زوج البيئة بالكامل عند وجود أي متغير منه؛ وإلا زوج local.properties المستبعد من Git. يمنع خلط ID من مصدر مع hash من مصدر آخر. الأخطاء عامة بلا قيم. لا حقول دخول أو اتصال خفي في هذه الشريحة.
4. ✅ Workflow مستقل موثوق، push للفرع الحالي أو تشغيل يدوي منه فقط، للمستودع والمالك المحددين. لا pull_request_target ولا أسرار في CI الخاص بطلبات الدمج. لا cache/build scan/artifact سرّي. التشغيل 35387386142 أثبت المسار دون تسريب.
5. ⏳ فحوص المصدر/JVM/lint/build نجحت، لكن بوابة UI كشفت Launcher ANR ثابتًا في تشغيلين مستقلين. الإصلاح المخطط لا يغير `windowReady=10s` أو IME=15s أو gap=-2..12dp، ويجب أن ينجح Android وnative قبل إغلاق البوابة كاملة.
6. ⏳ بعد نجاح بوابتي UI، تحديث الذاكرة وTESTING والسجل والخطة وPR، ثم الانتقال لشاشة الحساب وربطها بمنسق واحد وتخزين إذن الاستعادة؛ لا إعادة إرسال المعاينة 0.2 بوصفها نسخة الربط.

## الحدود والتراجع

التصميم والأيقونة وDataStore لا تتغير. لا طلب قيم سرية من المالك. الشريحة لا تضيف حسابًا أو تحفظ OTP أو كلمة مرور. بيانات العميل المضمنة في APK لاحقًا قابلة للاستخراج وليست سرًا محميًا من صاحب الجهاز. توقيع الإصدار الدائم لم يجهز؛ لا حذف بيانات لمعالجة اختلاف شهادات المعاينة. التراجع بــrevert لهذه الشريحة دون force أو دمج.

## المراجع الرسمية المقروءة

- https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets
- https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/references/buildconfig
- https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/Variant
- https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/BuildConfigField
- https://developer.android.com/reference/kotlin/android/app/KeyguardManager
- https://core.telegram.org/api/obtaining_api_id
