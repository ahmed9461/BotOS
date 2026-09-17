# ذاكرة BotOS — نقطة الاستئناف

2026-09-18. المهمة النشطة [0002-ci-recovery](tasks/0002-ci-recovery.md) و[تحقيق SDK](tasks/0002-sdk-discovery.md)، validating؛ تستكمل [0001](tasks/0001-foundation.md).

## المنتج والقواعد الثابتة

Android يجمع بوتات Telegram في تبويبات قابلة للتسمية والترتيب، ويعرض الرسائل والأزرار حسب الأنواع لا تكامل لكل بوت ولا AI يخمن الأفعال. البوت الجديد المدعوم لا يحتاج APK جديدًا؛ نوع بروتوكول جديد قد يحتاج تحديث المحرك.

عربيRTL وEnglishLTR، Dark/Light/System، Pearl/Graphite/Mauve بلا كحلي أو أخضر، أصول أصلية ناعمة، حركة قصيرة قابلة للمقاطعة وتقليل الحركة، لا هندسة في نصوص UI. إلزامي: قراءة AGENTS والذاكرة والبنية والخطة والكود، تخطيط قبل التعديل، اختبار وتوثيق قبل التسليم.

## مكان العمل والمنفذ

main عند ca4776f5f9bdf7cdac757daddb036a9722776c22 يحتوي التخطيط؛ التطبيق في feat/android-foundation وPR#1. أساس الكود0ac337e6507a04ab672190a7f3eb21ffe97cba69. لا تبدأ مجددًا اعتمادًا على main فقط.

خمس وحدات app/model/telegram/data/designsystem: Compose وNavigation3، ثيمات وأيقونة، DataStore للأسماء والترتيب والتفضيلات، نموذج رسائل وأفعال بعزل chat/account/revision، renderer ومعاينة محلية صريحة. الأسماء bookmarks غير متحققة؛ يمكن فتحها خارجيًا في Telegram. PreviewGateway مصطنع، المسودة/التمرير مؤقتان؛ لا دخول أو إرسال حي.

## أدلة البناء الحالية

النواة37 مسجلة محليًا في سجل الأساس. run35284171101 فشل PATH قبل Gradle. إصلاحca1ac533 في run35287275281 عثر على sdkmanager12.0؛ 10 اختبارات SDK والتباين14 نجحت؛ فشل معرف حزمة37.

run35287534204 عند81f0edd422417348567efb515c9739d755567112: repo checks وPython13 والتباين14 نجحت، وartifact10524798195 قُرئ. نشر جميع الاعتمادات الثمانية المختارة مؤكد في metadata الرسمي. SDK الفعلي ينشر **platforms;android-37.0** revision2 وليس platforms;android-37. لا حاجة لتغيير Kotlin/AGP أو اتهام أدواتSDK بالتقادم لحل هذا الخطأ.

كتب القرار قبل التصحيح في commit3f284f186494de9d70c99fdb644c9f69fe8892e5، ثم صُحّح workflow إلى معرف37.0 وتحديث BUILD. compileSdk/targetSdk37 والمكتبات بقيت كما هي. **إعادة البناء بعد هذا التصحيح لم يُتحقق منها بعد؛ لا APK ناجح مثبت.** لا قياس أداء أو اختبار جهاز/Telegram حقيقي.

## البناء والموانع

Kotlin2.4.20 + AGP9.3.1 + Gradle9.6.1/JDK21، BOM2026.09.00، Activity1.13.0، Lifecycle2.11.0، Nav3 1.1.7، DataStore1.2.1، Coroutines1.11.0. توفر artifacts ليس اختبار توافق. لا Android SDK محلي، DNS المباشر تعذر، connectorGitHub يعمل.

Wrapper رسمي مولد في CI ويثبت بعد المراجعة. لا Room مكرر لقاعدةTDLib أو WorkManager لاتصال حي أو Hilt دون حاجة. P2 يتطلب TDLib commit/JNI/schema متطابقة، بيانات تطبيق خارجGit، جلسة محمية وموافقة واختبارحساب؛ لا أكواد في المحادثة.

## الخطوة التالية الدقيقة

افحص run التالي لتصحيح معرفSDK: تأكد من نجاح install، ثم اقرأ wrapper/Gradle tests/lint/assemble. عند فشل جديد أضف دليله وخطة إصلاحه قبل التعديل. عند النجاح سجّل APK/checksum والاختبارات في TESTING والذاكرة والخطتين. لا تخلط تجميعAPK باختبار الواجهات أو إنهاءP2.

أدلة: https://github.com/ahmed9461/BotOS/pull/1 ، https://github.com/ahmed9461/BotOS/actions/runs/35287534204
