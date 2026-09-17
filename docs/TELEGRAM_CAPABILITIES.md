# قدرات Telegram وحدود التنفيذ

التحقق المرجعي: 2026-09-18. وجود ميزة في Bot API لا يعني تلقائيًا أن adapter TDLib أو renderer في BotOS يدعمها.

## مصادر موثقة

- سجل Bot API الرسمي يذكر Rich Messages منذ 10.1، وتوسعات 10.2 و10.3 (2026-08-24): https://core.telegram.org/bots/api-changelog
- مصدر المكتبة يذكر 1.8.67 في CMakeLists عند المراجعة، لكن master متحرك ولا يكفي للتثبيت: https://github.com/tdlib/td/blob/master/CMakeLists.txt
- مخطط العميل، المرجع الفعلي لأسماء وأنواع adapter: https://github.com/tdlib/td/blob/master/td/generate/scheme/td_api.tl
- شروط العميل: https://core.telegram.org/api/terms

## مصفوفة التسليم

| القدرة | الشريحة الأولى | قبل إصدار live |
|---|---|---|
| النص والكتل والأزرار | نموذج داخلي + معاينة محلية، عند تنفيذها | adapter من مخطط مثبت واختبار حقيقي |
| Inline callbacks | هوية ورسالة ومراجعة وأفعال opaque في النواة | إرسال getCallbackQueryAnswer الصحيح مع feedback |
| Reply keyboards | نموذج منفصل لا تحويله إلى callback | الإرسال بالنص والموافقة للطلبات الحساسة |
| تعديل الرسائل | اختبار استبدال in-place وأفعال قديمة | تحديثات TDLib مرتبطة بنفس chat/message |
| Rich Messages | renderer داخلي لجزء معلوم؛ ليس توافقًا كاملاً | أسماء/حقول schema، nested blocks، limits واختبارات |
| Streaming drafts | مؤجل | id مؤقت/إنهاء/إيقاف/عزل عدم حفظه كرسالة نهائية |
| صور/صوت/فيديو/ملفات | مؤجل | تنزيل مدروس، صلاحيات، limits ومعاينة حقيقية |
| Mini Apps/Payments/Login URLs | مؤجل | تنفيذ متخصص ومراجعة أمان؛ fallback صريح |
| أنواع جديدة غير معروفة | رسالة مفهومة لا crash | رابط آمن لـTelegram إن أمكن |

## ثوابت العميل

لا تضغط بناءً على اسم «رجوع» أو «التالي». احتفظ ببيانات ونوع الزر وسياق رسالته كما وردت. لا ترسل callback قديمًا بعد تغيير الرسالة. لا تكرر `/start` بمجرد التنقل. لا ترسل موقعًا أو رقمًا دون موافقة. فتح URL يحتاج scheme مسموحًا وفصل روابط النظام عن محتوى البوت.

المحتوى العادي الذي يتضمن جدول Markdown ليس بالضرورة Rich Message رسمية. نماذج العرض الداخلية ليست أسماء API. لا تكتب mapper قبل قراءة المخطط الذي بُني منه ملف JNI نفسه.
