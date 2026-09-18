# المهمة 0003 — إصلاح الإدخال وتحديث المظهر

التاريخ: 2026-09-18. الحالة: validating. الفرع feat/ui-refinement-v2 فوق914ab136 / PR1. خطة التنفيذ سبقت المصدر في3714c4af.

## الطلب والثوابت

المالك جرّب0.1: فراغIME كبير، switch لا يتحدث إلا بعد التنقل، ألوان ثقيلة وواجهات ضعيفة. وافق على إصلاح الاثنين وإعادة التصميم. **الحفاظ على أيقونة launcher حرفيًا** وعلىDataStore وأسماء البوتات وفكرة التبويبات. لا دخول Telegram وهمي ولا إضافة حساب حي في هذه الجولة.

## القراءة والتشخيص

قرئت AGENTS والذاكرة والبنية والخارطة والقرارات والسجل والتصميم ومصادر الشاشة/التخزين قبل تعديل التطبيق. عُزلت مسؤوليةIME الموزعة ولقطة preferences المحسوبة خارجNavEntry. التحقق المرجعي من وثائق Android الرسمية. لا إعادة تأسيس منmain ولا مسح عمل سابق.

## الخطوات

- [x] 1. كتابة خطة وذاكرة قبل التنفيذ.
- [x] 2. مالكinsets واحد فيBotOsApp باستخدام union للنظام/cutout/IME؛ لاimePadding فيcomposer/editor؛ إخفاءdock والرأس الموسع أثناء الكتابة.
- [x] 3. قراءةState داخل كلRoute، خاصةAppearanceRoute، بلا key قسري ولا إعادة فتح وجهة. switch يعرض قيمة وحالة نصية من نفس المصدر.
- [x] 4. لوحةPorcelain/Graphite/Iris، Theme tiles بمعاينات صغيرة، محرر أنظف، بطاقات/تبويبات وشريط إدخال جديد، مكتبة محلية قابلة للبحث؛ launcher لم يلمس.
- [x] 5a. كتابة3 اختبارات تشغيل للجهاز وworkflow محاكيAPI35 وصور وقياسgap. إضافةفحص ثبات الأيقونة وملكيةinsets وتعادل الترجمة.
- [ ] 5b. تشغيل وقراءةcore/lint/Android tests والصور، معالجة أي فشل.
- [ ] 6. اعتماد أدلة الاختبار والوثائق وAPK0.2 وchecksum وتسليم.

## معايير القبول

تبديل متكرر داخلالمظهر دون مغادرته، وحفظالقيمة والثيم عبرrecreation. حقل الكتابة أعلىIME بفاصل<=12dp عندالاستقرار، dock غير موجود وقتالكتابة. صورفاتح/داكن/كيبورد حقيقية، لا ادعاءقياسFPS. تشغيلالمحاكي لا يغني عن جهاز المالك ولوحةمفاتيحه. مكتبةالمستخدم لا تمس. الاختبارغيرالمشغّل غيرمتحقق.

## الملفات والأثر

app shell/routes/screens/renderer/resources/build/androidTest؛ core/designsystem؛ scripts/check_ui_contract وrun_ui_tests؛ workflow؛ docs. لا تغيير فيcore/model أوTelegram transport أومفاتيحDataStore أوملفاتlauncher.

## المخاطر والتراجع

تداخلinsets والتنقل وفقدالحالة واختلافلوحاتالمفاتيح: اختبارات فعلية مع تسجيلالقياس. أي فشل فيtest أوlint يمنع حزمةالتسليم؛ لاsuppression ولاbaseline. توقيعdebug مؤقت قد يمنع التثبيت فوق0.1؛ افحص الشهادة ووثق ذلك. التراجعrevert0003 لا يمسمرحلةالأساس.

## المصادر

https://developer.android.com/develop/ui/compose/system/insets-ui
https://developer.android.com/guide/navigation/navigation-3/save-state
https://developer.android.com/develop/ui/compose/testing
https://developer.android.com/jetpack/androidx/releases/test
https://developer.android.com/studio/run/emulator-commandline

## الدليل الحالي

فحص حسابي محلي لأزواجالألوان المقترحة14/14 اجتاز4.5:1؛ CI سيفحص الألوان من المصدر نفسه. لا نتيجةAndroid أوصورجهاز للنسخة0.2 فيلحظة هذاcommit. اعتماداتAndroidTest مثبتةjunit1.3.0/runner1.7.0 وComposeTest منBOMالمختبر؛ باقيالإصدارات لم تتغير.
