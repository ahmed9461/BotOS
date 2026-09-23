# الاختبارات والأدلة — BotOS

## 23 سبتمبر 2026 — owner delivery الأول توقف بعد نجاح الترقية

[owner delivery 35917995670](https://github.com/ahmed9461/BotOS/actions/runs/35917995670) قبل طلب CI88، وبنى baseline 0.4 بلا إعداد حساب. داخل خطوة المالك نجح probe الفعلي `owner_upgrade_0.4_to_0.5=passed` على محاكي بشهادة CI مؤقتة، أي ثبّت النسختين بلا uninstall وبقي ملف الاختبار والتفضيلات. بعده فشل `connectedOwnerPreviewAndroidTest` عند إعادة تغليف Gradle للحزمة بـ`INSTALL_FAILED_UPDATE_INCOMPATIBLE`: تغير سياق Android user/home بين بناء APK وتشغيل الاختبار فأصبح توقيع إعادة الحزم مختلفًا. **لا تقرير startup ناجح ولا CMS ولا APK مهيأ مسلّم من هذا التشغيل.** خطة التصحيح في المهمة 0007-E؛ طلب CI88 القديم عُطّل قبل مصدر جديد.

## 23 سبتمبر 2026 — بوابة مصدر ترقية المالك على CI88

[CI88/35916568481](https://github.com/ahmed9461/BotOS/actions/runs/35916568481) على `c59350836c2b14285e65500094a6c5849baf04e3` نجح. حزمة BotOS-runtime-checks-88/10775636751 طابقت SHA256 `9e72a377eeaf7d0e7c9e91aba52bc03838dd59aaade7ea3f5a2d938ff72fbef9`. XML: 105 JVM + 8 native + 77 app، صفر فشل/خطأ/تخطٍ. 21 لقطة، روجعت لقطة outgoing-voice-dark-ar؛ IME gap=6.095238dp. lint التطبيق 0 أخطاء/36 تحذيرًا والمحرك 0/0؛ native `accountUsed=false`. اجتازت اختبارات Python الخاصة بترتيب تثبيت 0.4→0.5 ورفض التوقيع والإصدار المختلف ضمن بوابة المصدر؛ **لم يعمل بعد بناء المالك المهيأ أو اختبار الترقية الفعلي على المحاكي الخاص**. تفعيل طلب التسليم من هذا المصدر هو البوابة التالية.

## 23 سبتمبر 2026 — مصدر إصدار 0.5 على CI87

[CI87/35913433566](https://github.com/ahmed9461/BotOS/actions/runs/35913433566) على `ef8fc65999a257b0f0100f214d3bb503119699cf` نجح بعد رفع الإصدار وحارس التسليم. حزمة BotOS-runtime-checks-87/10774886606 طابقت SHA256 `4bc8cada9df9b0a3a35823e279e7c52a959866ecb95a06b35375cda0eaf96073`. قرئت XML: 105 JVM و8 native و77 app، صفر فشل/خطأ/تخطٍ. 21 لقطة؛ روجعت outgoing-voice-dark-ar، وIME gap=6.095238dp. lint التطبيق 0 أخطاء/36 تحذيرًا، والمحرك 0/0؛ native `accountUsed=false`. بوابة owner delivery لم تعمل في هذا التشغيل؛ فحص 0.4→0.5 اللاحق يحتاج مصدرًا وCI جديدين.

## تجهيز مصدر0.5 قبل CI النهائي

اختبارات Python المحددة `OwnerRequestTest` وحالتا `OwnerPackagingTest` للإصدار والتغليف نجحت محليًا 8/8 بعد تغيير حارس الفرع وversionCode5. هذا لا يثبت بناء Android أو owner delivery للمصدر الجديد. `delivery/request.json` معطل حتى اجتياز CI المطابق.

## 23 سبتمبر 2026 — E التقنية على CI86

[CI86/35910856881](https://github.com/ahmed9461/BotOS/actions/runs/35910856881) على `5a009413b6ee60283992d5a07bb303027f661f86` نجح. حزمة BotOS-runtime-checks-86/10773497880 طابقت SHA256 `848f7e6a2e732f41b475d168e9219bfc5be6deea8f242c374cb91e1b00b0c6f4`. XML الفعلية:8model+97telegram=105JVM، و8native، و77app بلا فشل/خطأ/تخطي. الحالات الثلاث الجديدة `textAndRichStreamFragmentsReplaceInPlaceAndOldStopCannotEraseNewDraft`, `finalTextEditReplacesMessageAndInvalidatesPriorRevisionOnlyInItsChat`, `pendingTextAndRichUpdatesAreIsolatedAcrossTabSwitchAndDisconnect` موجودة وناجحة. فحص مصفوفة Rich 30/36 في بوابة العقد اجتاز. 21لقطة بينها outgoing-voice-dark-ar روجعت، وIME6.095238dp؛ lint المحرك0/0 والتطبيق0أخطاء/36تحذيرًا، native `accountUsed=false`. هذا دليل E التقني بموارد اصطناعية، وليس بناء المالك المهيأ أو توقيعًا أو تجربة حسابه.

## 23 سبتمبر 2026 — إغلاق D5 على CI85

[CI85/35902432267](https://github.com/ahmed9461/BotOS/actions/runs/35902432267) على `b4d08dc10dc80077bf948672f75ecd9584e908f5` نجح. حزمة BotOS-runtime-checks-85/10770880004 طابقت SHA256 `2435312cd4cf3d3c38de936269463373d507d7523809f8adaf156e4cef2022c7` وسلامة ZIP. قرئت XML: 8model+94telegram=102JVM، و8native، و77app بلا فشل/خطأ/تخطي. اختبار `voiceCaptureStopsWhenActivityLeavesForeground` موجود في التقرير وناجح. 21 لقطة بينها `outgoing-voice-dark-ar.png` روجعت بصريًا؛ keyboard gap=6.095238dp. lint المحرك0/0 والتطبيق0أخطاء/36تحذيرًا، وnative-runtime يثبت TDLib1.8.67 والمخطط المثبت و`accountUsed=false`. هذه بوابة محاكي D5 وليست تجربة حساب أو هاتف المالك، ولا APK مالك جديد.

## 23 سبتمبر 2026 — D4 على CI81

CI81/35897443037 على `5305c9da275e8cf5c9a169b640715eb64055dc20` اجتاز. حزمة10767727778 SHA256 `4d9bb3dd8eadb29c8a88d58a7beb992ae8f9c8dfade4a44c4f6d8bbf46a5370f` وسلامةZIP صحيحة. تقارير XML الفعلية:8model+94telegram=102JVM، و8native، و70app، دون فشل أو خطأ أو تخطي أو مدة غير صالحة. راجعت أسماء حالات OutgoingAttachmentPreparerTest الأربع، معاينة OutgoingAttachmentUiTest والقائمة في LiveBotPanelTest وحالات الاحتفاظ التسع والتنسيق الست، والـlogs الجديدة بلا انهيار. lint المحرك0/0 والتطبيق0أخطاء/35تحذيرًا؛20صورة وفجوةIME6.095238dp، ورجعت صورتا outgoing-preview-light-ar/dark-ar بصريًا داخل التصميم المعتمد. native-runtime يثبت المخطط المثبت و`accountUsed=false`. CI79 أخفق في نوع Application بتجميع التطبيق، وCI80 أخفق في استيراد Compose للاختبار؛ سبب كل تصحيح وخطته في 0007-outgoing-media-execution. D5 والمالك النهائي ليسا ضمن هذا الدليل.

## 23 سبتمبر 2026 — D3 على CI77

CI77/35893389553 على `4de8977126774dc46301525fc045f301c9112c3a` اجتاز. حزمة10765664792 مطابقةSHA256 `52fb64fd9510781f847244aaa36708bd5f3c049ba0133d901f1cb408788039ce`. قراءة XML:102JVM (8model+94telegram) و8native و62app، صفر فشل/خطأ/تخطي ومددصالحة. حالات TelegramUploadsTest الست وحالة عزل temporaryId فيJVM موجودة. lintالمحرك0/0 والتطبيق0أخطاء/30تحذيرًا؛18لقطة وIME6.095238dp، وnative `accountUsed=false`. المنسق والـRPC والسجل اجتازوا fixtures؛ المنتقي والمعاينة والتسجيل ليست ضمن هذا المصدر ولاAPKمالكجديد.

## 23 سبتمبر 2026 — D2 على CI75

CI75/35890816599 على `d98e1b73117831d8a307c1e0612beaf9ce9b418b` اجتاز. حزمة10765920308 مطابقة SHA256 `1c18be57c8bee84444feb4a9b8a1db82def4d7f9db9c9e509ee04ebd0e410bc6`. قراءة XML:101JVM (8model+93telegram) و8native و56app، دون فشل/خطأ/تخطي ومدد غير صالحة. OutgoingRetentionTest فيه7 حالات فعلية. lint المحرك0/0 والتطبيق0أخطاء/30تحذيرًا؛18لقطة وIME6.095238dp. CI74/35888950631 فشل في بوابة العدد49 السابقة رغم نجاح هذه الاختبارات؛ صُحح الشرط إلى56 مع التحقق من أسماء السبعة دون تخفيف صفر فشل/تخطي أو الصور. لا يثبت هذا وصلة TelegramUploads أو إرسال ملف من الواجهة.

## 23 سبتمبر 2026 — آخر بوابة D1

CI72/35878937444 على `6cee28e77b5a3dbcc5435cb532b33e2717a02a0a` اجتاز. نُزلت حزمة10760805798 وطابقت SHA256 `ff1dee867c087c687a775957b0f48d27f2465ea1565a21f2dfe5746ba794b881`. قراءة16 تقرير XML أعطت 8model+93telegram=101JVM، و8native، و49app، بلا فشل/خطأ/تخطي. يوجد18لقطة، وkeyboard gap=6.095238dp، وnative-runtime يثبت TDLib1.8.67 وaccountUsed=false. هذا دليل الهدف الثابت وسباق النتيجة، وليس دليل مخزن/واجهة/تسجيل D2 وما بعدها. تقارير 0.4 التاريخية أدناه تخص نسختها فقط.

## الحالة الأحدث — 23 سبتمبر 2026

B اجتازت CI60 على96869836:89JVM+8native+30app و13لقطة؛ قرئت XMLوالبصمة كما في0007-avatar-streaming-verification. Cمكتوبة، وبوابتها95JVM+8native+46app و16لقطة **غير مكتملة بعد**. نتائج0.4 أدناه تاريخية ولا تثبتC. آخرAPKمالك0.4، ولا تغير لهويته أو توقيعه.

## أدلة النسخة المسلّمة0.4

2026-09-20. المصدر84ff6ef85a5185f0fee0b955b8ff13009dbd5b33 اجتازالتكامل وبناءالمالكالمهيأ. الملفالنهائيوقعخارجCIبالمفتاحالدائمالمستعاد، وليسAPKغيرالمهيأمنPR.

## التكامل Run53

https://github.com/ahmed9461/BotOS/actions/runs/35526731984

Artifact10610291745، SHA256 `4fe1f4ca6358799d2df3380f6693a4d06201cab88eea1cb5d41d8fdfca78ddf0`. قُرئتXMLوتحققعدمفشل/خطأ/تخطٍ/مدةسالبةأوغيرمحدودة.

| الفحص | النتيجة |
|---|---|
| JVM |68، منها3modelو65telegram |
| JNI/Keystore |8علىمحاكيAPI35 |
| التطبيق |20حالةمنتهيةبنجاح |
| Python |56ناجحة |
| lint |المحرك0/0؛التطبيق0أخطاءو26تحذيرًا |
| الصور |11صورةفعليةلـfixturesوالواجهة |
| IME |gap6.095238dp،داخلحد-2..12dp |
| Native |1.8.67/d1085f9c،unicodepassed،accountUsed=false |

راجعتصورRichالفاتحوالداكنومبدّلالبوتات،والمظهر والمكتبة والمحرر والحساب والكيبورد. صورRichداخلComponentActivityلاتستخدمShellكاملًا؛لايُدعىأنهااختبارمحادثةحقيقيةأوحسابمالك. MainActivityواختباراتهاهيبوابةInsetsوالعربيةوالحفظوإعادةالإنشاء.

## التسليم المهيأ owner6

https://github.com/ahmed9461/BotOS/actions/runs/35527432601

Artifact10610437140طابقSHA256 `03fd88a7003add3e799d05112f876e4ce095ea19a25d5617477b306f57862d7c`. فُكCMSبالمفتاحالمستعاد،وطابقprovenanceالمصدر والتشغيل والهويةوالتهيئةوبصماتAPKوأداةالتوقيع.

OwnerBuildSmokeTestناجح1/1 في7.156ثانية:التهيئةموجودة،debuggable=false،الموافقةقبلحقولالدخولوالجلسة،ولاaccountاستُخدم. owner-update.jsonيثبتبقاءملفخاصوتفضيلاتمصطنعةعندإعادةتثبيتAPKنفسهبـinstall-rدونuninstallبشهادةCIالمؤقتة. **ليس اختبارترقيةرقمإصدارأواختبارالمفتاحالنهائيعلىجهازالمالك.**

## التوقيع النهائي

`BotOS-0.4.0-preview.apk`،87,154,161بايت،بهويةcom.ahmed9461.botos.app. SHA256:
`1ef4bbc9c224c508b8b09fc6950600d63d9d7a015788053bdac5453a0c47961a`.

apksignerتحققبـv2/v3وبشهادة6530ae8d20d9324504418f2afa274fec2cd1dc289ce3bdf2c8176f3620e7deba. استخدمPKCS12المستعادمنالنسخةالمحفوظة،لا مفتاحًا جديدًا. محتوىZIPغيرالتوقيعيطابقAPKقبلالتوقيع،وسلامةZIPومحاذاتهومكتبتاARM64/x86_64تحققت. حزمةالمفاتيحوملفالاستعادةسُلّماللمالكوحفظاخاصّينخارجGit.

## تعثرات محفوظة

Run52/35525449238اجتاز68JVMو8nativeو16appثمتوقف؛XMLأخيرةسالبةالمدةلمتعتبرنجاحًا. السجلأظهرإعادةإنشاءActivityبعدreadyعندLocale. خطة8f5ebb16سبقت84ff6ef8:ضبطالعربيةقبلالإطلاقوقاعدةComposev2المثبتةمعإبقاءكلالحالاتوالمهل. Run53أغلقالبوابة؛لمتخففالشروط.

Run50نجح68/8/19قبلشريحةالتسليم. Run49فشلsmartcastوRun48fixtureوصححاكمافيالخطط. Run47نجح62/8/16وRun46توقفبوابةتوثيقلمتلغ. تفاصيلالأدلةالأقدممحفوظةبالتاريخفيGitوخُطط0004/0005/0006 وdocs/history.

## حدود التسليم0.4 في تاريخه

لا تسجيلدخولحسابالمالكفيCIأومنالمساعد،ولا اختبارجهازبالتوقيعالنهائيأوكلالأجهزةأوالشبكةالضعيفةأوFPS/TalkBackشاملأوruntime16KB. محاذاةELFليستتشغيل16KB. وسائطRichلا تُنزَّلأوتُشغَّل،والرياضياتنصية،وstreaming/MiniApps/Paymentsمؤجلة.

## 23 سبتمبر 2026 — C للعرض المحلي

BاجتازتCI60على96869836:89JVM+8native+30app؛ digest وXMLفي0007-avatar-streaming-verification. الآن توجد6JVM و16Android جديدة و3لقطات لا نجاحتلقائيًا. المطلوب95JVM+8native+46app و16صورة. محليًا56Python و14تباين وXML/TOML وتجميعmodelناجحة فقط. راجع0007-playback-progress للحدود ودليلملفاتMP4/WebM/WAV/PNG/TGSالمصطنعة.
