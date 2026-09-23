# 0007-D — تنفيذ الإرسال والتسجيل فوق المصدر المجتاز

23 سبتمبر 2026. هذه الخطة مكملة لـ `0007-outgoing-media-ui.md` و`0007-upload-retention.md`، وتسبق أي كود جديد في شريحة D. نقطة البداية هي `7e70a66bfd7a1bd93b4bcc6c38cc1845a0ebebc4` بعد اجتياز CI64.

## دليل إغلاق C

CI64 / 35800651504 اجتاز على المصدر المذكور. قُرئت حزمة BotOS-runtime-checks-64: **96 JVM + 8 native + 49 app** بلا فشل/خطأ/تخطي، وفجوة IME = 6.095238dp. روجعت لقطتا received-video-ar وreceived-webm-ar، وكلتاهما من عارض الإنتاج بعد onRenderedFirstFrame. هذا يغلق C ضمن الترميزات الاصطناعية المختبرة فقط، ولا يعني دعم كل فيديو على كل هاتف.

## checkpoint بعد CI68 — قبل شريحة التخزين

المصدر `6e998eb9b4168ef003435c050dfb868b45f2bfc8` اجتاز CI68/35875429273: 100JVM + 8native + 49app، وكل XML بصفر فشل/خطأ/تخطي. قرئت حزمة runtime-checks المطابقة SHA256 `fa29c57fd1f74a615d8c7531a1d57d8c15ca1c7f72ef0cba23735a1c8fd401e1`. لذلك لا تعاد D1 من الصفر.

المراجعة بعد الدليل وجدت حالتين يجب إغلاقهما **قبل** المخزن: (1) نتيجة `updateMessageSendSucceeded/Failed` قد تسبق رد `sendMessage`؛ يجب حفظها مؤقتًا بمفتاح temporary id ثم ربطها عند ظهور `sending_id`، من دون إعادة إرسال. (2) recovery probe يجب أن يطابق userId و`generation` من `ChatKey.account`، لا userId وحده. بعد اختبار هاتين الحالتين تبدأ شريحة الاحتفاظ: `OutgoingMediaStore` تحت الجذر الخاص، ثم `OutgoingUploadJournal` ذري مكتوب قبل RPC. لا UI ولا ميكروفون ضمن هذه الشريحة.

## checkpoint بعد CI72 — الشريحة التالية D2

المصدر `6cee28e77b5a3dbcc5435cb532b33e2717a02a0a` اجتاز CI72/35878937444. نُزلت الحزمة10760805798 وطابقت SHA256 `ff1dee867c087c687a775957b0f48d27f2465ea1565a21f2dfe5746ba794b881`. قُرئت XML: 8model+93telegram=101JVM، و8native، و49app؛ صفر فشل/خطأ/تخطي. توجد18لقطة، وIME gap=6.095238dp، وملف native يثبت schema/commit والحساب غير مستخدم. هذا يغلق تصحيح race والتجميع؛ لا يثبت مخزنًا أو واجهة إرسال.

D2 بالترتيب: مخزن خاص يستورد stream محدودًا ويحتفظ بالملف حتى قرار صريح، ثم سجل ذري محدود يرفض فساد البيانات ويثبت الهدف والملف ومعرف الإرسال وحالة ما قبل RPC، ثم اختبارات الاستيراد/الحصة/الفساد/إعادة الفتح/عدم إخلاء غير المؤكد. أبقِ النسخ والاستيراد خارج Main ولا تنشئ جلسة لمجرد إنشاء المخزن. بعد اجتياز D2 اربط TelegramUploads بالمخزن والسجل مع عزل الحساب/الجيل والنتيجة السابقة للاستجابة قبل الواجهة. لا يُستنتج نجاح D من اختبار D1.

## تصحيح بوابة الدليل بعد CI74 — قبل تعديل السكربت

CI74/35888950631 على972fc778 وصل إلى 56 اختبار تطبيق و8native مع بناء ناجح، ثم رفض `check_device_evidence.py` العدد الجديد لأنه يطلب 49 حرفيًا. حزمة10764338042 طابقت SHA256 `da0c0e8e3e6ff938267ecfeda8bd8f11a932e2872b877712bfc0086d01a4c119`. XML المقروءة: 101JVM+8native+56app، كلّها بلا فشل/خطأ/تخطي ومدد صالحة؛ أسماء اختبارات OutgoingRetentionTest السبعة موجودة، و18لقطة وIME6.095238dp. لا تُنسب نتيجة workflow الفاشل لنجاح البوابة.

التصحيح المحدد: ارفع العدد المطلوب في سكربت الأدلة من49إلى56، مع فرض وجود الحالات السبع بالأسماء في `OutgoingRetentionTest`، وأبقِ شرط صفر فشل/خطأ/تخطي و18لقطة وقياسIME وسائر بوابات المحرك كما هي. ثم أعد CI على المصدر المصحح واقرأ XML والحزمة قبل إغلاق D2. لا تعديل لشرط اختبار موجود أو لتطبيق المالك.

## إغلاق D2 والانتقال إلى D3

CI75/35890816599 على `d98e1b73117831d8a307c1e0612beaf9ce9b418b` اجتاز. حزمة10765920308 طابقت SHA256 `1c18be57c8bee84444feb4a9b8a1db82def4d7f9db9c9e509ee04ebd0e410bc6`، وقُرئت XML:101JVM+8native+56app بلا فشل/خطأ/تخطي ومدد غير صالحة؛ السبع الجديدة مثبتة بالاسم. lintالمحرك0/0 والتطبيق0أخطاء/30تحذيرًا،18لقطة وIME6.095238dp. مخزن D2 وسجله اجتازا، ولم يرتبطا بعد بواجهة أو RPC حقيقي.

D3 وفق الخطوة4 أدناه: اجعل UploadEvent يحمل هوية الحساب/الجيل من اشتراك TDLib، ثم منسقًا واحدًا على مستوى Application يحجز الملف والسجل قبل RPC، ويتابع pending/final حتى بعد تبديل البوت. افحص ازدواج ضغطات الإرسال وسباق terminal قبل response والتبديل والخروج والتعطل؛ إذا ضاعت نتيجة الطلب، احتفظ بالملف ولا تعِد send. عند الاستعادة لا تستعلم إلا عن temporaryMessageId معروف وبعد تحقق هوية الحساب والشات، ولا تنسب ردًا قديمًا لجيل جديد. لا منتقي أو ميكروفون قبل إغلاق هذه الوصلة. اختبار JVM لعزل الحدث والسجل واختبار Android لربط التطبيق؛ ثم قراءة CI وXML.

## نطاق D الفعلي

1. CaptureTarget عام محدود من BotConversations: معرّف الحساب/جيله وChatKey/chatId فقط بعد READY. لا RPC أو ReadyAccount يُكشف للواجهة.
2. OutgoingMediaStore داخل noBackupFilesDir/telegram/main/files/botos_outgoing:
   - حد ملف 50MiB، إجمالي 200MiB، عدد 32.
   - نسخ URI المختار خارج Main وبشكل متدفق.
   - الصور تُعاد JPEG بدون EXIF، ضلع أقصى1600، ≤10MiB، ومجموع الأبعاد/النسبة ضمن TDLib.
   - الفيديو: MP4/H264 فقط كفيديو؛ غير ذلك DOCUMENT مع رسالة واضحة قبل الإرسال، بلا transcoding خفي.
   - الصوت: ملف معروف AUDIO، والتسجيل VOICE بمخرجات M4A/AAC mono كما يسمح المخطط المثبت.
3. OutgoingUploadJournal ذري قبل RPC: UUID داخلي، sending_id موجب فريد، target account/chat، path داخلي، الحالة، messageId إن عرف. لا caption أو محتوى الملف في السجل.
4. TelegramUploads على مستوى Application، تستعمل account/RPC داخل core:telegram لا Composable. تربط response/update بالـsending_id/temporary message id، ولا تعيد send بعد timeout. success النهائي وحده يسمح بإخلاء الملف. unknown يحتفظ بالملف.
5. واجهة Compose:
   - زر مرفق صغير بجانب composer.
   - PhotoPicker للصورة/الفيديو وOpenDocument للصوت/الملف.
   - Preview قبل send يوضح البوت والنوع والحجم، ويمكن إضافة caption.
   - تبديل البوت بعد فتح picker أو أثناء التحضير يلغي preview قبل RPC.
   - التسجيل يبدأ فقط بعد RECORD_AUDIO صريح؛ حالة وعدّاد وإيقاف ثم preview، ويتوقف عند ON_STOP.
6. الاختبارات:
   - JVM للسجل وsending_id وسباقات response/update/timeout وعدم إعادة الإرسال.
   - Android لاختيار/معاينة/إلغاء/تبديل target/رفض الإذن ودورة التسجيل.
   - fixtures فقط، لا حساب مالك ولا ملف خاص.
   - بوابات C كلها تبقى دون تخفيف.
7. بعد D فقط تبدأ E: مصفوفة Rich/Streaming النهائية، ثم owner delivery من مصدر مجتاز ومهيأ وموقع بالشهادة الثابتة الحالية.

## المراجع الرسمية التي أعيد التحقق منها

- Android Photo Picker: https://developer.android.com/training/data-storage/shared/photo-picker — PickVisualMedia لصورة/فيديو واحد، وfallback تلقائي إلى ACTION_OPEN_DOCUMENT على الأجهزة غير المدعومة.
- Android media recording APIs: https://developer.android.com/media/platform/mediarecorder
- TDLib schema المثبت: https://github.com/tdlib/td/blob/d1085f9cebc5a62379991ae1652673954f229c1f/td/generate/scheme/td_api.tl

لا صلاحية قراءة مكتبة كاملة، لا WebView، لا Base64 للفيديو، ولا إرسال بمجرد الاختيار.
