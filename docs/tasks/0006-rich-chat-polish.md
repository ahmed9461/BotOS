# 0006 — Rich Messages وتكثيف شاشة المحادثة

التاريخ: 2026-09-19. الحالة: planned. الفرع: `feat/rich-chat-polish` فوق `b251ae91e635bb9b68cd82a539709cf070a2b6c1`، وهو آخر مصدر 0.3 سلّم للمالك. هذه الخطة كتبت قبل أي تعديل وظيفي.

## ملاحظات المالك التي يجب حلها

بعد تجربة الحساب والبوتات فعليًا، أكد المالك أن الوظائف الأساسية تعمل، لكنه قيّم عرض الرسائل والأزرار وتوزيع العناصر بأقل من المستوى المطلوب. المطلوب:
- دعم Rich Messages الرسمية بدل `Block.Unsupported` وعبارة «المحتوى غير متاح».
- فصل أزرار البوت بصريًا عن فقاعة النص؛ النص داخل الفقاعة، وصفوف الأزرار أسفلها.
- جعل فقاعات الرسائل تلتف حول المحتوى بدل ملء معظم العرض، وإظهار الوقت وحالة الإرسال بحجم صغير في سطر مدمج.
- إلغاء شريط تبويبات البوتات الكبير من أعلى المساحة؛ استبداله بمبدّل صغير بسهم يفتح قائمة البوتات المحفوظة وينتقل مباشرة للمحادثة.
- تقليل ارتفاع رأس المحادثة وعناصرها لزيادة مساحة الرسائل.
- عدم تغيير «مكتبتي» و«المظهر» إلا إذا لزم تصحيح مشترك لا يمكن عزله.

## قراءة البروتوكول قبل الكود

المحول الحالي في `BotMessages.kt` لا يدعم إلا `messageText`، وكل محتوى آخر يتحول إلى `Unsupported`. المخطط المثبت المستخدم فعليًا في المشروع (TDLib 1.8.67، commit `d1085f9cebc5a62379991ae1652673954f229c1f`) يحتوي بالفعل:
- `messageRichMessage message:richMessage`
- `richMessage blocks:vector<PageBlock> is_rtl is_full`
- RichText: plain/bold/italic/underline/strikethrough/spoiler/subscript/superscript/marked/date/mention/hashtag/cashtag/bank-card/bot-command/fixed/user mention/url/email/phone/custom emoji/icon/math/button/diff/reference/anchor/composite.
- PageBlock: title/subtitle/author-date/header/subheader/section-heading/kicker/paragraph/preformatted/footer/thinking/divider/math/anchor/list/block quote/expandable quote/pull quote/animation/audio/document/photo/video/voice-note/cover/embed/embed-post/collage/slideshow/chat-link/table/details/related-articles/map/button-row/unsupported.
- Rich button rows تستعمل `inlineButton` و`InlineKeyboardButtonType` نفسها؛ لا يجوز تخمين callback من النص.

مرجع Bot API الرسمي يؤكد Rich Messages والعناوين والقوائم والجداول والوسائط والاقتباسات والتفاصيل والصيغ والمسودات، وتوسعت الأزرار والملفات في 2026. التنفيذ يعتمد مخطط TDLib المثبت لا أسماء Bot API المترجمة مباشرة.

المراجع:
- https://core.telegram.org/bots/api#rich-messages
- https://core.telegram.org/bots/api-changelog
- https://github.com/tdlib/td/blob/d1085f9cebc5a62379991ae1652673954f229c1f/td/generate/scheme/td_api.tl

## الخطة المرتبة

1. **نموذج العرض:** إضافة نموذج StyledText مستقل عن Compose يحفظ النص وعلامات التنسيق والروابط، ونماذج Rich block اللازمة دون تمرير JSON إلى الواجهة. الحفاظ على النماذج القديمة للمعاينة حتى لا تتكسر.
2. **محول Rich Message:** قراءة `messageRichMessage` وكل `PageBlock` الموجود في المخطط المثبت بحدود عمق/عدد/نص. الأنواع النصية والجداول والقوائم والتفاصيل والاقتباسات والأزرار تُحوّل بالكامل. أنواع الوسائط تُحوّل إلى نموذج Media block مع metadata/caption بدل «غير متاح»؛ تنزيل/تشغيل الملف لا يُدعى إن لم ينفذ.
3. **RichText renderer:** تحويل StyledText إلى AnnotatedString مع bold/italic/underline/strike/code/marked/link/spoiler تمثيل آمن، واتجاه RTL من الرسالة لا من التخمين. الروابط تظل بحاجة إلى مسار موافقة آمن عند التنفيذ.
4. **فصل الأزرار:** يقسم MessageRenderer كتلة المحتوى عن `Block.Buttons`. فقاعة النص لا تحتوي صفوف الأزرار؛ الأزرار تظهر تحت الفقاعة بعرض صفوف منظم ومتجاوب، مع الحفاظ على ActionTicket والمراجعة.
5. **فقاعة محادثة مضغوطة:** wrap-content مع حد أقصى مناسب، padding أصغر، وقت الرسالة من `date`، وحالة الإرسال الصغيرة بجانبه. لا بطاقة كبيرة لـ /start ولا سطر «أرسلت» منفصل يضاعف الارتفاع.
6. **رأس ومبدّل البوت:** عند محادثة بوت حية لا يظهر LazyRow للتبويبات. يظهر سهم صغير/زر تبديل يفتح قائمة البوتات المحفوظة؛ اختيار عنصر يستدعي نفس `onSelect`. رأس البوت يصبح compact مع الاسم/username/قائمة المزيد فقط. «المعاينة» تبقى متاحة من المبدّل دون احتلال شريط دائم.
7. **Reply keyboard:** يبقى قرب composer كلوحة مستقلة، مع صفوف wrap منظمة لا horizontal scroll طويل، وتمييزها عن inline buttons أسفل الرسالة.
8. **اختبارات:** 
   - محول Rich Message: جميع عائلات RichText وPageBlock الحالية، جدول/list/details/button-row، unknown/depth/limits، RTL، timestamp.
   - renderer/UI: الأزرار خارج bubble، outgoing bubble لا يملأ العرض، سطر time/delivery، مبدّل البوتات، عدم ظهور التبويبات القديمة، Library/Appearance regression unchanged.
   - الإبقاء على كل بوابات 0.3: JVM/JNI/Keystore/app/IME/contrast/icon.
9. **الدليل والتسليم:** لا إعلان دعم «كل الأنواع» إلا بقدر المخطط المختبر. إذا بقيت الوسائط كبطاقات metadata فقط، يكتب ذلك صراحة. بعد نجاح CI واللقطات يحدّث PROJECT_MEMORY/CHANGELOG/TELEGRAM_CAPABILITIES/LIVE_BOTS/UI_GUIDELINES/TESTING وPR.

## معايير القبول

- رسالة PixelPilot Rich لا تظهر «المحتوى غير متاح» إذا كانت مكونة من PageBlock مدعوم في المخطط.
- العناوين/النصوص/القوائم/الجداول/quotes/details/code/math/button rows تظهر بترتيب الرسالة واتجاهها.
- Inline/Rich buttons أسفل فقاعة المحتوى، وليست داخلها.
- رسالة `/start` الصادرة تلتف حول النص، والوقت + الحالة في سطر صغير داخل أسفل الفقاعة.
- شاشة المحادثة تستعيد مساحة رأسية واضحة؛ لا LazyRow للبوتات في الأعلى، ومبدّل صغير يفتح البوتات المحفوظة.
- «مكتبتي» و«المظهر» لا يتغير تصميمهما.
- لا كسر لعزل الحساب/المحادثة أو أمان callbacks أو قواعد insets أو أيقونة launcher.

## المخاطر والتراجع

أكبر خطر هو تحويل RichText المتداخل إلى نموذج عرض يفقد المعنى، أو جعل زر Rich ينفذ نوعًا غير مدعوم. لذلك التحويل محافظ: النوع غير المعروف يصبح عنصرًا واضحًا/معطلًا ولا يُخمن. لا تحميل وسائط تلقائيًا ولا تنفيذ login/webapp/payment. التراجع عبر revert لشريحة0006 فقط دون مسح مكتبة المستخدم أو الجلسة أو إعادة تصميم الأقسام الأخرى.
