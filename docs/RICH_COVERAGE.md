# تغطية Rich في مخطط TDLib المثبت

23 سبتمبر 2026. المرجع هو `td_api.tl` من commit `d1085f9cebc5a62379991ae1652673954f229c1f`، SHA256 `326b65b41442901ad6bf0ca2f7c356ae54365d6c343956a62e06a8b3cb305e87`. حزمة عقد CI85 تحتوي النسخة التي بُني منها JNI. هذه المصفوفة تسرد **30 نوع RichText و36 نوع PageBlock**، ولا تخلطها مع Bot API.

«ضمن النطاق» تعني أن التحويل والعرض في BotOS ينفذان الوظيفة المذكورة بحدود `ContentLimits`، ولا تعني تطابقًا بصريًا كاملًا مع Telegram أو اختبار كل جهاز. «جزئي» يعني أن جزءًا من حقول البروتوكول أو سلوكه يفقد أو يقدم بديلًا. «غير منفذ» يعني أنه لا توجد وظيفة مقابلة؛ البديل الآمن موضح. المصدر: `BotMessages.kt` للمحول، `MessageRenderer.kt` و`ReceivedMediaUi.kt` للعرض. الاختبارات القائمة: `BotMessagesTest`, `RichAdapterRegressionTest`, `RichChatRegressionTest`, `ReceivedMediaUiTest`. تظل الفجوات التي لا تختبر صفًا بعينه موثقة ولا تتحول إلى ادعاء اختبار شامل.

## RichText

| النوع | الحالة | التحويل والعرض والحد |
|---|---|---|
| `richTextPlain` | ضمن النطاق | نص محفوظ داخل حد الأحرف. |
| `richTextBold` | ضمن النطاق | وزن عريض في StyledText. |
| `richTextItalic` | ضمن النطاق | ميل النص. |
| `richTextUnderline` | ضمن النطاق | خط سفلي. |
| `richTextStrikethrough` | ضمن النطاق | شطب. |
| `richTextSpoiler` | ضمن النطاق | مخفي في العرض والدلالة حتى كشف صريح؛ لا فعل قبل الكشف. |
| `richTextSubscript` | ضمن النطاق | خفض خط الأساس وتصغير الخط. |
| `richTextSuperscript` | ضمن النطاق | رفع خط الأساس وتصغير الخط. |
| `richTextMarked` | ضمن النطاق | خلفية تمييز. |
| `richTextDateTime` | جزئي | يعرض `text` فقط؛ يتجاهل `unix_time` ونوع التنسيق. |
| `richTextMention` | جزئي | يعرض `text`؛ لا انتقال تلقائي إلى username. |
| `richTextHashtag` | جزئي | يعرض `text`؛ لا بحث الوسم. |
| `richTextCashtag` | جزئي | يعرض `text`؛ لا بحث الرمز. |
| `richTextBankCardNumber` | جزئي | يعرض `text`؛ لا فعل نسخ أو دفع. |
| `richTextBotCommand` | جزئي | يعرض `text`؛ لا إرسال آلي للأمر. |
| `richTextFixed` | ضمن النطاق | خط أحادي العرض. |
| `richTextMentionName` | جزئي | يعرض `text`؛ لا استخدام `user_id` للتنقل. |
| `richTextUrl` | جزئي | رابط HTTP(S) آمن وتأكيد قبل الفتح؛ لا cache semantics أو schemes أخرى. |
| `richTextEmailAddress` | جزئي | يعرض `text`؛ لا فتح بريد آلي. |
| `richTextPhoneNumber` | جزئي | يعرض `text`؛ لا اتصال أو مشاركة رقم. |
| `richTextCustomEmoji` | جزئي | `alternative_text` فقط؛ لا أصل emoji مخصص. |
| `richTextIcon` | جزئي | رمز بديل ثابت؛ لا رسم `document`. |
| `richTextMathematicalExpression` | جزئي | صيغة نصية أحادية العرض؛ لا تنضيد رياضي. |
| `richTextButton` | جزئي | نص وفعل callback/URL المدعوم فقط، مربوط بمراجعة الرسالة؛ الأنواع الأخرى معطلة. |
| `richTextDiff` | جزئي | القديم مشطوب والجديد معروض؛ لا دلالات diff أخرى. |
| `richTextReference` | جزئي | يعرض النص؛ مرجع `name` لا يتحول إلى هدف. |
| `richTextReferenceLink` | جزئي | رابط HTTP(S) بتأكيد؛ اسم المرجع غير مستخدم. |
| `richTextAnchor` | غير منفذ | لا تعرض عقدة anchor ولا تنقل إلى موضع؛ يظل باقي النص سالمًا. |
| `richTextAnchorLink` | جزئي | URL خارجي آمن فقط؛ رابط anchor داخلي غير منفذ. |
| `richTexts` | ضمن النطاق | تجميع العناصر بالترتيب ضمن حدود العمق والعقد. |

## PageBlock

| النوع | الحالة | التحويل والعرض والحد |
|---|---|---|
| `pageBlockTitle` | ضمن النطاق | عنوان من RichText. |
| `pageBlockSubtitle` | ضمن النطاق | عنوان فرعي. |
| `pageBlockAuthorDate` | جزئي | المؤلف ظاهر؛ `publish_date` غير معروض. |
| `pageBlockHeader` | ضمن النطاق | عنوان مستوى ثانٍ. |
| `pageBlockSubheader` | ضمن النطاق | عنوان مستوى ثالث. |
| `pageBlockSectionHeading` | ضمن النطاق | مستوى مضبوط بين 1 و6. |
| `pageBlockKicker` | ضمن النطاق | عنوان صغير. |
| `pageBlockParagraph` | ضمن النطاق | RichText في فقرة. |
| `pageBlockPreformatted` | جزئي | نص أحادي العرض مع اتجاه LTR؛ `language` محفوظ بالنموذج دون تلوين syntax. |
| `pageBlockFooter` | ضمن النطاق | نص تذييل. |
| `pageBlockThinking` | جزئي | يعرض كاقتباس؛ لا حالة تفكير متخصصة. |
| `pageBlockDivider` | ضمن النطاق | فاصل مرئي. |
| `pageBlockMathematicalExpression` | جزئي | صيغة نصية أحادية العرض؛ لا تنضيد LaTeX. |
| `pageBlockAnchor` | غير منفذ | لا مرساة أو انتقال؛ لا يضاف محتوى وهمي. |
| `pageBlockList` | ضمن النطاق | ترتيب وتسميات وخانة اختيار للعرض فقط، وحد 100 عنصر؛ لا تغيير حالة Telegram. |
| `pageBlockBlockQuote` | ضمن النطاق | كتل متداخلة ونسبة المصدر ضمن حد العمق. |
| `pageBlockExpandableBlockQuote` | ضمن النطاق | نص ونسبة المصدر مع فتح/طي محلي. |
| `pageBlockPullQuote` | ضمن النطاق | نص ونسبة المصدر. |
| `pageBlockAnimation` | جزئي | وسائط محلية مطلوبة وحدود ترميز؛ spoiler يظل مطويًا قبل الطلب. |
| `pageBlockAudio` | جزئي | تشغيل ملف محلي مدعوم بطلب صريح؛ لا وعد بكل codec. |
| `pageBlockDocument` | جزئي | بيانات الملف ظاهرة؛ لا عارض عام لكل صيغة. |
| `pageBlockPhoto` | جزئي | تنزيل وصورة وتكبير ضمن الحدود؛ spoiler محمي، لا ضمان لكل ملف. |
| `pageBlockVideo` | جزئي | تشغيل محلي بطلب صريح؛ MP4/WebM fixtures فقط، spoiler محمي. |
| `pageBlockVoiceNote` | جزئي | تشغيل محلي مدعوم؛ لا وعد بكل codec أو waveform. |
| `pageBlockCover` | ضمن النطاق | يحول الكتلة الداخلية ضمن حد العمق. |
| `pageBlockEmbedded` | جزئي | بطاقة URL/أبعاد/وصف فقط؛ لا HTML أو WebView أو scripts. |
| `pageBlockEmbeddedPost` | جزئي | المؤلف والكتل والوصف؛ الصورة والتاريخ وURL لا تعرض كمنشور كامل. |
| `pageBlockCollage` | جزئي | معرض كتل محلي محدود؛ لا تخطيط Telegram مطابق. |
| `pageBlockSlideshow` | جزئي | معرض كتل محلي محدود؛ لا تنقل شرائح متخصص. |
| `pageBlockChatLink` | جزئي | اسم ورابط آمن عند توفر username؛ الصورة ولون المحادثة غير معروضين. |
| `pageBlockTable` | جزئي | خلايا وصفوف وامتداد وعنوان؛ bordered/striped غير ممثلين. |
| `pageBlockDetails` | ضمن النطاق | عنوان وكتل متداخلة وحالة فتح أولية؛ طي محلي. |
| `pageBlockRelatedArticles` | جزئي | نصوص العنوان والوصف والرابط؛ ليست بطاقات روابط كاملة. |
| `pageBlockMap` | جزئي | بطاقة مع إحداثيات؛ لا خريطة تفاعلية أو مشاركة موقع. |
| `pageBlockButtonRow` | جزئي | ترتيب وأفعال callback/URL المعروفة فقط مع تأكيد/مراجعة؛ الأنواع غير المعروفة معطلة. |
| `pageBlockUnsupported` | ضمن النطاق | بديل مفهوم وآمن؛ لا crash ولا فعل تخميني. |

## حدود مشتركة

المحول يحد العمق والكتل والنص والأفعال، والواجهة تحد الوسائط والصفوف. الرسالة المؤقتة لا تحفظ كتاريخ، وأفعالها معطلة حتى وصول رسالة نهائية. الروابط الخارجية تمر بفحص HTTP(S) وتأكيد، ولا يُنشأ WebView أو RPC من نص المحتوى. نجاح الاختبارات الاصطناعية لا يثبت كل ترميز أو جهاز أو حساب المالك. Mini Apps وPayments وLogin URLs والتغطية البصرية الكاملة لـemoji/رياضيات/مراجع خارج هذه المصفوفة وغير مكتملة.
