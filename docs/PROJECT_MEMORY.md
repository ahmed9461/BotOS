# ذاكرة BotOS — نقطة الاستئناف

آخر تحديث: 2026-09-19. الفرع النشط `feat/rich-chat-polish` فوق `b251ae91e635bb9b68cd82a539709cf070a2b6c1`. المهمة النشطة: [0006 — Rich Messages وتكثيف شاشة المحادثة](tasks/0006-rich-chat-polish.md). **الخطة 4f8e790c كتبت قبل أي تعديل وظيفي.**

## قبل العمل

اقرأ AGENTS وهذه الذاكرة والبنية والخارطة والقرارات والأمان والقدرات وخطة0006 ثم الكود والاختبارات. افحص أحدث الفرع وCI والفرق، ثم نفذ بالترتيب واختبر واقرأ الأدلة ووثق. لا تخفيف بوابة أو إعادة فشل عشوائيًا أو اعتبار وجود اختبار نجاحًا. لا force-push ولا مسح بيانات أو بناء من main.

## ما اعتمده المالك وما لا يتغير

الهوية Porcelain / Graphite / Iris والأيقونة والثيمات وRTL معتمدة. **قسم مكتبتي والمظهر ممتازان حسب تجربة المالك بتاريخ2026-09-19 ولا يغيران في0006 إلا لتصحيح مشترك لا يمكن عزله.** BotOsApp وحده يملك systemBars/displayCutout/IME؛ لا imePadding إضافي. مفاتيح WorkspaceStore وأسماء البوتات وترتيبها محفوظة.

## حالة 0.3 التي جربها المالك

المصدر `b251ae91` اجتاز Android foundation run36 `35401850435`: integration/native arm64-v8a+x86_64 وverify-runtime ناجحة. Private owner delivery run4 `35401845819` نجح في بناء نسخة مهيأة غير قابلة للتصحيح، اختبار الموافقة قبل الدخول، والتغليف الخاص. سُلّمت نسخة BotOS 0.3.0 Preview للمالك، وهو سجّل الدخول واستخدم بوتات حقيقية بنفسه.

تعليق المالك بعد الاستخدام:
- الوظائف العامة تعمل تقريبًا 70%.
- توزيع العناصر واحترافية شاشة المحادثة يحتاجان تحسينًا كبيرًا.
- عرض الرسائل والأزرار ضعيف حاليًا.
- رسالة Rich Message حقيقية من PixelPilot تحولت إلى «المحتوى غير متاح للعرض هنا بعد».
- Inline/Reply buttons تحتاج ترتيبًا شبيهًا بتجربة Telegram، خارج فقاعة النص.
- فقاعة الرسالة الصادرة (/start) كبيرة جدًا بسبب fillMaxWidth وحالة «أُرسلت» في سطر مستقل.
- شريط تبويبات البوتات الكبير يستهلك مساحة؛ المطلوب سهم/مبدّل صغير يفتح البوتات المحفوظة وينتقل بينها.

## السبب التقني المثبت

`BotMessageAdapter.message` في `BotMessages.kt` يعالج فقط `messageText`. كل محتوى آخر يصبح `Block.Unsupported`. لذلك `messageRichMessage` لا يصل إلى renderer رغم أن TDLib المثبت يدعمه.

المخطط المثبت نفسه (TDLib1.8.67 commit `d1085f9cebc5a62379991ae1652673954f229c1f`) يحتوي `messageRichMessage message:richMessage` و`richMessage blocks:vector<PageBlock> is_rtl is_full`، وجميع RichText/PageBlock الحديثة بما فيها headings/lists/tables/details/quotes/math/thinking/media/buttonRow/document/expandable quote. **لا حاجة لترقية TDLib لمجرد هذه المشكلة؛ الأولوية تفعيل ما هو موجود في المخطط المثبت والمختبر.**

المراجع الرسمية:
- https://core.telegram.org/bots/api#rich-messages
- https://core.telegram.org/bots/api-changelog
- https://github.com/tdlib/td/blob/d1085f9cebc5a62379991ae1652673954f229c1f/td/generate/scheme/td_api.tl

## الثوابت الأمنية والوظيفية

الحساب يستخدم TDLib الحقيقي، AccountCoordinator، SessionVault/Keystore وتهيئة GitHub Secrets الموثوقة. لا OTP/password/session/api_hash في Git أو logs أو المحادثة. BotConversations يتحقق من chatTypePrivate وuserTypeBot. callback من بيانات البروتوكول فقط، لا من نص الزر. /start فعل صريح. الروابط تحتاج موافقة. لا إعادة إرسال تلقائية بعد timeout. عزل الحساب/المحادثة/جيل العرض/مراجعة الرسالة يجب ألا ينكسر في0006.

## نطاق0006

1. نموذج StyledText/Rich blocks مستقل عن Compose.
2. محول `messageRichMessage` لكل PageBlock الحالي في المخطط المثبت، مع limits واتجاه RTL.
3. عرض التنسيق الغني والجداول والقوائم والاقتباسات والتفاصيل والصيغ والأزرار. أنواع الوسائط لا تتحول إلى «غير متاح»؛ إن لم ينفذ تشغيل/تنزيل كامل تعرض بطاقة نوع/metadata/caption بوضوح ولا يدعى اكتمال playback.
4. فصل الأزرار عن فقاعة النص.
5. فقاعات wrap-content بوقت وحالة إرسال صغيرة.
6. إزالة LazyRow الدائم للبوتات من شاشة المحادثة واستبداله بمبدّل صغير بسهم وقائمة محفوظات.
7. تكثيف رأس المحادثة مع إبقاء المكتبة والمظهر دون تغيير.
8. اختبارات بروتوكول/renderer/UI وإبقاء بوابات الحساب/JNI/Keystore/IME/icon/contrast.

## أول خطوة تالية

عدّل نموذج العرض والمحول أولًا، واكتب اختبارات Rich Message من JSON مطابق للمخطط المثبت قبل لمس الواجهة. بعد اجتياز التحويل، حدّث MessageRenderer، ثم Workspace/LiveBotPanel، ثم شغل CI كاملًا وافتح اللقطات. لا تعلن «دعم كل Rich Messages» قبل نتيجة فعلية لكل عائلة في المخطط.

## حدود ما بعد0006

وسائط Rich قد تحتاج مرحلة تنزيل/تشغيل فعلية إذا بقيت بطاقة metadata فقط. Streaming drafts وMiniApps/Payments/Login URLs وطلب الهاتف/الموقع ليست جزءًا من هذه الشريحة إلا إذا كانت ضرورية لإصلاح رسالة المستخدم الحالية. FPS/TalkBack وشبكة ضعيفة وأجهزة متعددة/runtime16KB والنشر العام ما زالت بوابات مستقلة.
