# 0007-D — تنفيذ الإرسال والتسجيل فوق المصدر المجتاز

23 سبتمبر 2026. هذه الخطة مكملة لـ `0007-outgoing-media-ui.md` و`0007-upload-retention.md`، وتسبق أي كود جديد في شريحة D. نقطة البداية هي `7e70a66bfd7a1bd93b4bcc6c38cc1845a0ebebc4` بعد اجتياز CI64.

## دليل إغلاق C

CI64 / 35800651504 اجتاز على المصدر المذكور. قُرئت حزمة BotOS-runtime-checks-64: **96 JVM + 8 native + 49 app** بلا فشل/خطأ/تخطي، وفجوة IME = 6.095238dp. روجعت لقطتا received-video-ar وreceived-webm-ar، وكلتاهما من عارض الإنتاج بعد onRenderedFirstFrame. هذا يغلق C ضمن الترميزات الاصطناعية المختبرة فقط، ولا يعني دعم كل فيديو على كل هاتف.

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
