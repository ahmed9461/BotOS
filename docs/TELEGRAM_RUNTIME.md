# محرك Telegram — P2a

## حدود الوحدات ومصدر الحقيقة

core:telegram يبقى Kotlin/JVM مستقلًا عن Android. PreviewGateway القديم منفصل؛ TdRpc وTdTransport وAuthorization تنفذ عقد النقل والتفويض، لا تعرض JSON للمستخدم. core:tdlib وحدة Android جديدة لواجهة JSONJava الرسمية وKeystore وملفات الجلسة. يعتمد app على المعاينة فقط حتى اكتمال P2b؛ لا تشغيل تلقائي للنقل عند فتح شاشة.

البروتوكول المثبت: tdlib/td عند d1085f9cebc5a62379991ae1652673954f229c1f، الإصدار1.8.67. ملف schema SHA256:326b65b41442901ad6bf0ca2f7c356ae54365d6c343956a62e06a8b3cb305e87. ملف Java SHA256:e92666f288e599d1c55bf5d24774f5e3e890b0999b0629931a7cf627a4323510. تبنى JNI من المصدر نفسه، لا dependency جاهزة مجهولة.

## دورة الحياة

AndroidTelegramSession.open يستدعى خارج مسار UI، ويحجز رمز ملكية حصريًا. لا يحفظ بيانات حساب ولا يتصل بحساب عند الانتظار الأول للمعلمات. initialize يحتاج بيانات التطبيق وحماية الجلسة وموافقة صاحب الحساب في P2b. تمر حالات التفويض من updates فقط. الإرسال ينتظر جوابًا مرتبطًا بطلبه، ولا يعاد تلقائيًا عند المهلة أو الإلغاء.

close يغلق المكتبة ويحافظ على البيانات المشفرة. logOut ينتظر إقرار المكتبة وإغلاقها ثم يمحو ملفات الجلسة ومفتاحها، لا أسماء البوتات أو إعداداتها. فشل الشبكة لا يؤدي إلى محو. الطلبات المتأخرة لا تتحول إلى نجاح جديد، وإغلاق جلسة قديمة لا يحرر ملكية جلسة أحدث.

ملف مفتاح القاعدة ليس المفتاح نفسه: غلاف AES-GCM ومفتاح تغليف غير قابل للتصدير من Android Keystore. لا تشمل هذه الحماية جهازًا مخترقًا أو مراجعة أمنية شاملة. توضع الملفات في noBackupFilesDir، وتبقى مراجعة سياسة النسخ/النقل في app شرطًا قبل الاحتفاظ بحساب حقيقي.

## البناء والتحقق

native/dependencies.lock.json يثبت TDLib وOpenSSL وNDK وABI. scripts/build_tdlib.sh يبني arm64-v8a وx86_64 ويحفظ المصدر والترخيص والبصمات. فحص ELF alignment عند16KB لا يعني أن runtime اختبر على جهاز16KB؛ يجب تمييزهما.

اختبارات JVM تستخدم مدخلات مصطنعة. اختبارات Android تحمل المكتبة الفعلية وتختبر Keystore دون حساب أو credentials. نجاحها لا يغني عن اختبارات login/logout/reconnect وبوت حقيقي في P2b. حالات registration/premium/device-confirmation معروفة ولكن شاشاتها وآثارها ليست منفذة.

## مصادر

- https://github.com/tdlib/td/tree/d1085f9cebc5a62379991ae1652673954f229c1f/example/android
- https://core.telegram.org/tdlib/docs/td__json__client_8h.html
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/guide/practices/page-sizes
