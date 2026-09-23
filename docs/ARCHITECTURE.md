# بنية BotOS

آخر مراجعة:23سبتمبر2026. ست وحدات فعلية دونطبقاتشكلية. راجعTESTINGوالذاكرة لمصدرالدليل؛ الكودالمكتوبليسنجاحًا.

| الوحدة | المسؤولية |
|---|---|
| core:model | المكتبة والرسائل وStyledText وRich والوسائط والأفعال وحدودالمحتوى؛ مستقلةعنAndroid |
| core:telegram | TdRpc والتفويض وAccountCoordinator وBotConversations والمحول والمختزل وTelegramFiles وBotProfiles وPendingReplies |
| core:tdlib | JSONJava وAndroidTelegramSession وSessionVault؛ المخطط وJava وJNIمنالمصدرنفسه |
| core:data | WorkspaceStore وAccountRestoreStore وLocalAvatarStoreللصورالمحلية |
| core:designsystem | الألوان والأشكال والحركة والرموز والأيقونةالمعتمدة |
| app | تركيبالمنسقاتوالـViewModelsوالشاشاتوالعرض؛ لاتنفيذاتصالداخلComposables |

## الحساب والجلسة

UIintent → AccountCoordinator → AccountSession/TdRpc → TDLibupdates → state → AccountRoute.

الموافقةتسبقالجلسة. READYوgetMeيحددانالحساب؛ لاافتراضلنجاحالدخولمنOkعام. المدخلاتمؤقتة، والتخزينخارجالرسم. closeيحفظالجلسة، وlogOutيمحوهابعدإقرارTelegramوالإغلاق. فقدالمفتاحلايمحوالبياناتخفيًا. قاعدةTDLibمحميةبمفتاحKeystoreومستبعدةمنbackup.

## المحادثة والبث

ReadyAccount → BotConversations → searchPublicChat/getUser → botverification → history/updates → reducer → LiveBotPanel.

يلزمchatTypePrivate وuserTypeBot. التاريخمحدود؛ الأجيالومراجعاتالرسائلتعزلالنتائجوالأفعال. المهلةلاتعيدالفعل، و/startليسأثرتنقل. التعديلاتالمبكرةلاتضيع ومعرفالإرسالالمؤقتلايعودبعدنجاحه.

PendingRepliesمنupdatePendingMessageمستقلعنتاريخالمحادثةوعنمسودةالمستخدم. مدتهحسبالخيارالمثبت، وأفعالهمعطلةقبلالنهائي. يعرضهTimelineبمفتاحمستقلويحدثهفيمكانه؛الإيقافيتحققمنChatKey وdraftIdوcanStop. لاتاريخأوSentمصطنعانللمؤقت. ربط العرض اجتازCI60 ضمن الشريحةB.

## الصور والملفات

BotOsApplicationيملكTelegramFiles/BotProfiles/BotAvatarsمرةواحدة. الملفاتتعزلبالحسابوجيلالجلسةوبحدتوازي؛ pathsمنTDLibفقط. BotProfilesيتحققمنهويةالبوتدونفتحالشاتأوإرسال/start.

LocalAvatarStoreمستقلعنبياناتالمكتبةوالجلسة: المفتاحbookmarkid+username، والمجلدnoBackup/bot-avatars. URIمنPhotoPickerاختارهالمستخدم، حد16MiB، إعادةJPEG512pxوكتابةAtomicFileمحكومة. الصورةالمعروضة160pxوالفكخارجMain. التحليلاتلاتقرأملفًامنبوتنصي.

BotAvatarsيجمعالأسماءوالصوروالتنزيلاتوالحساب؛ الأولويةمحليةثمTelegramثمالحرف. الصورالواردةتمسحمنحالةالعرضعندالخروج/تبدلالجيل، أماالتخصيصالمحليفتزيينللمكتبةالمحلية. لايُرسلإلىTelegram. AvatarViewModelيحفظهدفاختيارالصورةعبرإعادةإنشاءالنشاطفقط، لاتحويلنتيجةمنتقيإلىالبوتالذيأصبحظاهرًاحديثًا.

## Rich والعارض

messageRichMessageيحوّلمنالمخططالمثبتبميزانياتأثناءالاجتياز. الجداولوالاقتباساتوالتفاصيلتحفظالبنية. الروابطوأزرارالنصتمرعبرActionTicketوموافقة، لافتحخارجيآلي. getFullRichMessageللمحتوىالجزئيبتوازي2ومراجعةوجيل. المضمنةالمؤقتةغيرقابلةللتنفيذحتىالرسالةالنهائية.

الأزرارالخارجيةأسفلالفقاعةوترتيبهاحسبالبروتوكولوالوقتمنdate. عرض الوسائط فيC يرتبط بفهرس الرسالة ومراجعتها واجتازCI64؛ لا تنفيذ HTML/JavaScript.

## احتفاظ المرفقات الصادرة — D2

OutgoingMediaStore ينسخ stream إلى noBackup/telegram/main/files/botos_outgoing خارج Main، بحصص50MiB/200MiB/32. OutgoingUploadJournal مستقل وذري، يحجز الهدف/الملف/sendingId قبلRPC؛ مرحلةATTEMPTED تمنع التكرار، وUNKNOWN تحتفظ بالملف، وSUCCEEDED النهائي وحده يجيز إخلاءه. لا caption أو محتوى الملف في السجل. سبع حالات Android اجتازتCI75؛ TelegramUploads والواجهة والتسجيل التالية في D3 وما بعدها.

## التصميم والتسليم

Navigation3والـRoutesتجمعالحالةداخلNavEntry. المبدّلبسهمورجوعالنظاملايضغطأزرارالبوت. BotOsAppوحدهمالكsystemBars/displayCutout/IME. المكتبةوالمظهرمحفوظتان؛ الصورةتحلنفسشارةالحرففقط.

PRCIبلاأسرار. هويةownerPreviewهيcom.ahmed9461.botos.app، والتوقيعالدائمخارجGitويستعادمنLibraryالخاصة. owner-deliveryمنمصدرمجتازوبحمولةCMSمشفرّة، لاAPKغيرمهيأبدلالمطلوب. راجعOWNER_DELIVERY.

## الحدود

تشغيل الوسائطC اجتازCI64 ضمن fixtures، وإرسال المرفقات والتسجيلD لم يكتمل؛ MiniApps/Paymentsوالتنضيدالكاملومصفوفةالأداءليستمكتملة. لاتدعمختباراتصورمصطنعةفحصالهواتفالحقيقيةأوالشبكةالضعيفةأوالـFPS.

## تحديث0007-C — تحققCI64

الصور والبثB مجتازانفيCI60. CتضيفMediaReference وفهرس رسائل نهائية→ReceivedMediaController→TelegramFiles→MediaDecoder→ReceivedMediaItem/Viewer. المنسق يملك الطلبات والفك وحالةالمعاينة؛ Composables لا تفتحجلسةأوتقرأمسارًا مننص. ثمانمعاينات، وفكبتوازي2، ومشغلواحدمحليبطلبصريح. إطارالعارضيغلقعندSTOP أو تغيرالمحادثةوالحساب؛ FileDataSourceيمنعشبكةمنالمشغل. CاجتازتCI64. DإرسالالمرفقاتوEالمصفوفةوالتسليمغيرمكتملةبعد.
