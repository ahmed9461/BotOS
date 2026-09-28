# 0008 — مراجعة CI100 البصرية قبل التسليم

28 سبتمبر 2026. متابعة المهمة النشطة؛ لا دمج أو تغيير هوية التثبيت.

## الدليل

CI100 /36410074465 نجح على9db53623ce165a1758feaf609611f5f14c752c36. الحزمة10964343045 طابقتSHA256 `7ad6b466cad8c4bee8fd14ba8c2f24c940b903d2a5799ad141265497e05a983b`، وCRC والمسارات؛ مصدر الأرشيف أعادtree `2b2b166db0591a5599f5524ccd32c8e21c6aafb2`. XML203حالات:110JVM+8native+85app بلا فشل/خطأ/تخطٍ. حالتا المتابعة والرجوع معIME نجحتا، ولا تعاد كتابتهما.

فُتحت لوحات الصور27. لقطةoutgoing-voice-dark-ar فارغة فعلًا؛ اختبارها القديم يستخدمtakeScreenshot مباشرة دون انتظار إطار نافذةModalBottomSheet. اختبارات المنطق نجحت لكن الصورة ليست دليلًا مرئيًا صالحًا. كذلك appearance-restored-ar أظهرت شريطي النظام فاتحين وأيقونات فاتحة مع محتوى داكن؛ التطبيق يغير لون الأيقونات فقط بينما إعدادedge-to-edge الأول يستنتج مظهر النظام لا اختيار التطبيق.

## التعديل المرتب

1. اربط OutgoingAttachmentUiTest بمساعدcaptureCommittedScreen الموجود بالفعل، دون إعادة رسم أو استبدال الصورة. حافظ على جميع التأكيدات وحالات التسجيل الثلاث.
2. اجعلAppFrame مالك نمط شرائط النظام أيضًا: استدعاءenableEdgeToEdge بأسلوبSystemBarStyle.auto يعتمد لون المظهر الفعلي، مرة لكلactivity/light بدل الاقتصار علىicon flags. الشريطان شفافان وinsets كما هي؛ لا هوامش ثابتة أو مالكIME إضافي. هذا يفيد التطبيق ومساراتاختباره التي تستخدمAppFrame.
3. حافظ على الإصدارات المثبتة (Activity1.13.0)، لا ترقية إلى1.14alpha. راجع الصور والـIME بعد CI جديد مطابق، خصوصًا التسجيل والمظهر المستعاد والفاتح/الداكن.
4. لا تُنسب الصور القديمة للمصدر الجديد؛ ابقِrequest.disabled حتى203حالة و27صورة صالحة ومراجعةالمصدر. ثمowner delivery0.6 وترقية0.5→0.6 والتوقيع الدائم.

النطاق:BotOsApp، OutgoingAttachmentUiTest، الوثائق فقط. لاRPC/تخزين/توقيع/launcher. مصدرScreenCapture الحالي لا يتغير؛ لا إعادةمحاولةلاختبارفاشل أوخفضالعدد. تراجع بعكسcommitالتعديل، دونforce-push.

مراجع رسمية روجعت28سبتمبر2026:
- https://developer.android.com/develop/ui/compose/system/system-bars
- https://developer.android.com/reference/androidx/activity/SystemBarStyle — APIموجودمن1.8؛ إهماله في1.14alpha لا يغيّر النسخةالمثبتة.
- https://github.com/androidx/androidx/blob/androidx-main/activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt — فصل إعداد النافذة عنلونالأيقونات.
