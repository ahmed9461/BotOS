# البنية المعمارية

آخر مراجعة: 2026-09-18. ست وحدات بعد إضافة محرك P2a المستقل. واجهة التطبيق الحالية ما زالت معاينة محلية؛ لا اعتبار لإضافة وحدة نقل على أنها إكمال ربط الحساب.

## التدفق وحدود الوحدات

`UI intent → ViewModel → Repository/Gateway → immutable state → Compose`

- core:model: نماذج Kotlin/JVM، وتطبيع الأسماء، وهوية الرسائل وعزل الحالة وحماية الأفعال القديمة.
- core:telegram: PreviewGateway المصطنع منفصل عن TdRpc/TdTransport/Authorization الجديدة. لا اعتماد Android هنا؛ JSON محصور في عقد النقل ولا يصل إلى الواجهة.
- core:tdlib: وحدة Android/JNI وSessionVault وAndroidTelegramSession. بنيت لتجميع واجهة JSONJava الرسمية وحماية مفاتيح قاعدة الجلسة وتوحيد ملكية الاستقبال. سبب الفصل هو اختلاف دورة حياة native واختبار Keystore عن النواة، وليس إنشاء وحدات فارغة.
- core:data: DataStore للأسماء والترتيب والتفضيلات، بنسخة واحدة لكل ملف؛ لا نسخ لقاعدة TDLib.
- core:designsystem: الألوان والأشكال والحركة والرموز المعتمدة. أصول launcher محفوظة كما أقرها المالك.
- app: نقطة التجميع وViewModels والتنقل والشاشات وrenderer. لم تضف علاقة app→core:tdlib حتى اكتمال P2b؛ واجهة المعاينة لا تفتح جلسة خفية.

عند الربط: `TDLib update → adapter → normalized message/action → per-chat state → renderer`. عمليات الشبكة والتخزين خارج مسار UI. لا يعتمد Adapter على أسماء الأزرار أو يتوقع schema غير التي بُنيت منها المكتبة.

## مصادر الحقيقة

DataStore مسؤول عن الاسم المحلي والترتيب والتفضيلات. TDLib مسؤول عن الجلسة والمحادثات والرسائل بعد الربط. ViewModel يملك حالة العرض والانتظار والمسودة المؤقتة. لا تستخدم أسماء التبويبات بدل account/chat؛ لا تتسرب أفعال بوت إلى آخر. مفاتيح التخزين الحالية لم تتغير.

## التنقل والحالة

Navigation3 يحتفظ بسجل الشاشات. الوجهات الجذرية: المساحة، المكتبة، المظهر، وإضافة/تعديل اسم بوت. كل Route يجمع حالته بـcollectAsStateWithLifecycle داخل NavEntry، لا لقطة Preferences قديمة خارج الوجهة. لا تعِد إنشاء الشاشة قسرًا لتحديث المفتاح. المسودة في SavedStateHandle، والتمرير والمحرر محفوظان ضمن عمرهما، وليس ذلك أرشيف محادثات دائمًا.

رجوع النظام لا يضغط زر رجوع البوت. لا تكرر /start عند فتح كل تبويب؛ بدء المحادثة فعل صريح عند الحاجة بعد تنفيذ النقل.

## مساحات النظام ولوحة المفاتيح

BotOsApp وحده يملك union لمساحات systemBars/displayCutout/IME، ويطبق windowInsetsPadding مرة واحدة مع contentWindowInsets صفرية في Scaffold واستهلاك padding المحتوى. لا معالجة مكررة في composer أو المحرر. يختفي التنقل وتتقلص المقدمة وقت الكتابة دون مساحة محجوزة لشريط مخفي. لون الخلفية يرسم قبل المساحات. لا تغيير في هذه القواعد أثناء دمج المحرك.

## محرك الجلسة

[TELEGRAM_RUNTIME](TELEGRAM_RUNTIME.md) يحدد ترتيب updates، والارتباط بالطلب والمهلة والإلغاء، وحالات التفويض، وclose مقابل logOut، ومفتاح Keystore والفشل المغلق. ملف native/dependencies.lock.json مرجع المصدر والأدوات، لا master متحرك. لا توكنات بوتات أو إعادة callbacks تلقائيًا بعد المهلة.

## المحتوى والأداء

MessageTimeline يربط الحساب والمحادثة والرسالة والمراجعة؛ التعديل يستبدل الرسالة في موضعها ويرفض فعلًا قديمًا. النموذج الداخلي ليس ادعاء دعم Telegram Rich Messages كاملًا. قوائم كسولة ومفاتيح مستقرة وحدود للحجم والعمق، ولا تحريك تقرير طويل كامل عند كل تحديث أو تحميل وسائط كبيرة تلقائيًا. القياس على جهاز شرط لادعاء معدل إطارات.

اختبارات النواة والجهاز وUI منفصلة. نجاح بناء JNI لا يعني حسابًا متصلًا، ومحاذاة ELF لا تعني اختبار runtime16KB. تبقى مقارنة الأيقونة والتباين وملكية المساحات ضمن البوابات.

## المراجع

- https://developer.android.com/topic/architecture
- https://developer.android.com/guide/navigation/navigation-3/save-state
- https://developer.android.com/develop/ui/compose/system/insets-ui
- https://core.telegram.org/tdlib
