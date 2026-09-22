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

PendingRepliesمنupdatePendingMessageمستقلعنتاريخالمحادثةوعنمسودةالمستخدم. مدتهحسبالخيارالمثبت، وأفعالهمعطلةقبلالنهائي. يعرضهTimelineبمفتاحمستقلويحدثهفيمكانه؛الإيقافيتحققمنChatKey وdraftIdوcanStop. لاتاريخأوSentمصطنعانللمؤقت. ربطالعرضالجديدينتظرCIفيالشريحةB.

## الصور والملفات

BotOsApplicationيملكTelegramFiles/BotProfiles/BotAvatarsمرةواحدة. الملفاتتعزلبالحسابوجيلالجلسةوبحدتوازي؛ pathsمنTDLibفقط. BotProfilesيتحققمنهويةالبوتدونفتحالشاتأوإرسال/start.

LocalAvatarStoreمستقلعنبياناتالمكتبةوالجلسة: المفتاحbookmarkid+username، والمجلدnoBackup/bot-avatars. URIمنPhotoPickerاختارهالمستخدم، حد16MiB، إعادةJPEG512pxوكتابةAtomicFileمحكومة. الصورةالمعروضة160pxوالفكخارجMain. التحليلاتلاتقرأملفًامنبوتنصي.

BotAvatarsيجمعالأسماءوالصوروالتنزيلاتوالحساب؛ الأولويةمحليةثمTelegramثمالحرف. الصورالواردةتمسحمنحالةالعرضعندالخروج/تبدلالجيل، أماالتخصيصالمحليفتزيينللمكتبةالمحلية. لايُرسلإلىTelegram. AvatarViewModelيحفظهدفاختيارالصورةعبرإعادةإنشاءالنشاطفقط، لاتحويلنتيجةمنتقيإلىالبوتالذيأصبحظاهرًاحديثًا.

## Rich والعارض

messageRichMessageيحوّلمنالمخططالمثبتبميزانياتأثناءالاجتياز. الجداولوالاقتباساتوالتفاصيلتحفظالبنية. الروابطوأزرارالنصتمرعبرActionTicketوموافقة، لافتحخارجيآلي. getFullRichMessageللمحتوىالجزئيبتوازي2ومراجعةوجيل. المضمنةالمؤقتةغيرقابلةللتنفيذحتىالرسالةالنهائية.

الأزرارالخارجيةأسفلالفقاعةوترتيبهاحسبالبروتوكولوالوقتمنdate. وسائطالرسائلمازالتبطاقاتبياناتحتىالشريحةC؛ لاتنزيلعامأوتشغيلمنHTML/JavaScript.

## التصميم والتسليم

Navigation3والـRoutesتجمعالحالةداخلNavEntry. المبدّلبسهمورجوعالنظاملايضغطأزرارالبوت. BotOsAppوحدهمالكsystemBars/displayCutout/IME. المكتبةوالمظهرمحفوظتان؛ الصورةتحلنفسشارةالحرففقط.

PRCIبلاأسرار. هويةownerPreviewهيcom.ahmed9461.botos.app، والتوقيعالدائمخارجGitويستعادمنLibraryالخاصة. owner-deliveryمنمصدرمجتازوبحمولةCMSمشفرّة، لاAPKغيرمهيأبدلالمطلوب. راجعOWNER_DELIVERY.

## الحدود

تشغيلوسائطالرسائل وإرسالالمرفقات والتسجيلمازالC/D؛ MiniApps/Paymentsوالتنضيدالكاملومصفوفةالأداءليستمكتملة. لاتدعمختباراتصورمصطنعةفحصالهواتفالحقيقيةأوالشبكةالضعيفةأوالـFPS.
