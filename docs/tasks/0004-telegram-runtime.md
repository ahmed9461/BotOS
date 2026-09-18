# المهمة 0004 — محرك Telegram المستقل

التاريخ: 2026-09-18. الحالة: **completed ضمن نطاق P2a**. الفرعfeat/telegram-runtime وPR3، بدأ من8aceb1a8. الخطة الأصلية09aaffca سبقت التنفيذ. المصدر النهائي المختبرe9b45c60؛ تفاصيل الإغلاق في[0004-validation-complete](0004-validation-complete.md).

## النطاق والقراءة

قرئت AGENTS والذاكرة والبنية والخارطة والقرارات والتصميم والأمان والقدرات والمخطط المثبت وخطط0003. المطلوب بناءTDLib الحقيقي وعقد نقله وحالات التفويض وحماية مفتاح الجلسة واختبارها، دون تغيير التصميم أو أيقونةlauncher أو مفاتيحDataStore. P2b لربط التطبيق بحساب المستخدم مرحلة منفصلة، وليست منجزًا ضمن هذه المهمة.

## خطوات الخطة وأدلة الإكمال

- [x] 1. مراجعة أدلة0.2 وشهادةAPK وبصمةWrapper وتوثيق اختلاف توقيعdebug؛ لا حذف صامت لبيانات المستخدم. Wrapper لم يثبت فيGit بعد، وهو حد موثق للبناء.
- [x] 2. تثبيت المصدر وبناءJSONJava الرسمي لـarm64-v8a وx86_64 معschema وJava والتراخيص والبصمات. نجح35372781224، وأعيد التحقق في35380280277.
- [x] 3. فصل core:telegram/JVM عن core:tdlib/Android-JNI-Keystore. تحديثARCHITECTURE وTELEGRAM_RUNTIME بالعقود وأسباب الفصل.
- [x] 4. اختبارات ارتباطالطلبات/الإلغاء/المهلة/الإغلاق/العزل والتفويض؛21حالةJVM إجمالية بلا فشل/تخطي. لا Ok وهمية تعني دخولًا.
- [x] 5. ثماني حالات جهازJNI/Keystore نجحت؛ ملف الجهاز يثبتالمصدر والعربية/emoji وانتظارالمعلمات بدون حساب. التلف أو فقدالمفتاح لا يمحو البيانات.
- [x] 6. اختبارات الواجهةالثلاثة واللقطاتالسبع وقياسالكبيورد نجحت، معاختبار مستقلAndroid ناجح. قُرئت الأدلة ووُثقت فيTESTING والذاكرة والخارطة وملحق الإغلاق. وصفPR يحدّث بهذهالنتائج، دوندمج تلقائي.

## سجل تعديلات الخطة

تصحيح تسجيلJNI عبر public variant API موثق في0004-build-api-fix و0004-native-verification. حفظnative-runtime ضمن الأرشيف موثق في0004-evidence-finalization. فشلIME ثم تعليق0/3 بعد تداخل مزامنةنافذة الاختبار موثقان في0004-ime-test-synchronization و0004-window-polling. كلتصحيح سبقه تخطيط ولم يخفف شروطالاختبارات أو يغيرواجهةالإنتاج.

## المصادر المثبتة

TDLib1.8.67 عندd1085f9cebc5a62379991ae1652673954f229c1f؛OpenSSL3.5.7LTS عند8cf17aaeb4599f8af87fefd810b5b5fee90fe69e؛NDK29.0.14206865. المرجعnative/dependencies.lock.json، وschema وJava من المصدر نفسه.

- https://core.telegram.org/tdlib/getting-started
- https://core.telegram.org/tdlib/docs/td__json__client_8h.html
- https://github.com/tdlib/td/tree/d1085f9cebc5a62379991ae1652673954f229c1f/example/android
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/guide/practices/page-sizes

## الحدود والتراجع

لا app integration أو حسابمالك أو OTP أو إرسال إلىبوت حي. لااختبارruntime16KB أوFPS أوأمان شامل. close يحافظ علىالجلسة، logOut ينتظرالإقرار والإغلاق ثميمحو ملفاتها، ولايتحولفشل الشبكةإلىحذف. التراجععبرrevert لشريحةالمحرك دونتغيير0.2 أو بياناتالمستخدم، ولاforce-push. التاليخطةP2b قبلالكود.
