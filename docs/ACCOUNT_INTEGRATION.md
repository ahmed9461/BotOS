# توصيل الحساب بالتطبيق — شريحة 0005

2026-09-18. التنفيذ مكتوب والتحقق الجديد جارٍ؛ لا حساب مستخدم استُخدم ولا APK مهيأ سُلّم. هذا الملف يحدّث وصف المعاينة السابق في ARCHITECTURE/SECURITY/TELEGRAM_RUNTIME لشريحة الحساب فقط. محادثات البوتات ووسائطها خطوة تالية.

## التدفق الفعلي

BotOsApplication يملك AccountCoordinator واحدًا لكل العملية. AccountViewModel يمرر أفعال الحساب؛ AccountRoute يجمع الحالة داخل NavEntry. يفتح المصنع AndroidTelegramSession على Dispatchers.IO، ولا اتصال عند التثبيت الجديد أو غياب الإعدادات. تبدأ الاستعادة فقط إذا كانت راية إذنها محفوظة سابقًا. لا عميل جديد لكل تبويب.

core:data يعتمد عقد ConnectionPreferences من core:telegram. AccountRestoreStore له ملف preferences مستقل داخل noBackupFilesDir؛ لا يغير WorkspaceStore أو أسماء البوتات أو ثيماتها. app يعتمد core:tdlib الحقيقي؛ لم تُستبدل المكتبة بنموذج اتصال مصطنع.

## الواجهة والخصوصية

وجهة الحساب من شاشة المظهر بنفس نظام التصميم والتبويبات والأيقونة. غياب تهيئة البناء يعرض رسالة واضحة بلا حقول دخول. الموافقة تسبق أول فتح، والخطوات تتبع AccountCoordinator وTDLib، لا استنتاج نجاح من Ok. الهاتف والكود وكلمة المرور والبريد بحسب الحالة. الطرق الإضافية غير المنفذة تعرض حالتها دون تخطيها أو إجراء شراء.

المدخلات remember وليست rememberSaveable أو SavedStateHandle. تُمسح من العرض بعد إرسالها وتبدل المرحلة، ولا تسجل. FLAG_SECURE يحمي نافذة المدخلات ويعيد العلم السابق عند خروجها. لا ادعاء مسح فوري للسلاسل من JVM أو حماية جهاز مخترق. الرجوع من الشاشة ليس خروجًا؛ الخروج المؤكد يمر بالمحرك وينتظر الإقرار والإغلاق قبل محو الجلسة. فشل الشبكة لا يمحوها.

Manifest يحدد BotOsApplication وINTERNET، ويمنع backup مع قواعد صريحة لكل من cloud-backup وdevice-transfer؛ noBackupFilesDir يحتوي راية الاستعادة ومخزن الجلسة. لا API hash أو رموز دخول في ملفات المصدر أو السجلات.

## البناء والاختبارات

Android foundation يستدعي tdlib-native عبر workflow_call من نفس المصدر، بلا secrets أو inherit. يبني/يتحقق من native للمعماريتين، ثم يشغّل JVM وlint التطبيق والمحرك وتجميعهما، ثم محاكيًا واحدًا لكل اختبارات الجهاز. لا تكرار لمحاكي مستقل لنفس المصدر، ولا حذف لاختبار. مهمة تهيئة الأسرار منفصلة ولا ترفع الملفات المولدة.

الجديد:4حالات AccountScreen (تهيئة/موافقة/انتقال/مدخلات مؤقتة وFLAG_SECURE وخروج)، و2حالات AccountRestoreStore بملفات حقيقية معزولة، و1تنقل للحساب في التطبيق غيرالمهيأ. يبقى36JVM و8native و3UI، أي10حالات جهاز app مع الجديدة. اختبارات عرض حالات الحساب مصطنعة ولا تثبت اتصالًا بخوادم Telegram. StateRestorationTester يختبر استعادة Compose لا موت العملية بالكامل.

## الأدلة عند البداية

ca668e00 اجتاز35389683160 و35389683150. قُرئت XML36JVM و8JNI/Keystore و3UI، وnative-runtime1.8.67 بالمصدرd1085f9c والعربية/emoji وaccountUsed=false؛ gap6.095238dp. حزمة10565762221 طابقت SHA25603d121d9f535945e79da90e34a8f2c71b7349cb44433a129c1da5b6f33233ab0. نجاحها لا يثبت الاختبارات الجديدة التي تنتظر CI.

## المراجع

https://developer.android.com/security/fraud-prevention/activities
https://developer.android.com/identity/data/autobackup
https://developer.android.com/develop/ui/compose/testing/common-patterns
https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows
