# ذاكرة BotOS — نقطة الاستئناف

آخر تحديث: 2026-09-18. المهمة النشطة: [0002-ci-recovery](tasks/0002-ci-recovery.md)، الحالة **validating**؛ تستكمل بوابة التحقق في [0001-foundation](tasks/0001-foundation.md).

## المنتج والمتطلبات الثابتة

Android يجمع بوتات Telegram المختارة في تبويبات قابلة للتسمية والترتيب، ويعرض الرسائل والأزرار حسب أنواعها دون تكامل خاص بكل بوت. لا AI يخمن callback. إضافة بوت يستخدم أنواعًا مدعومة لا تتطلب تحديث APK؛ نوع جديد قد يتطلب تحديث المحرك.

العربية/RTL أولًا، English/LTR سليم، Dark/Light/System، هوية Pearl/Graphite/Mauve لا كحلي أو أخضر، أيقونات أصلية ناعمة، حركة قصيرة قابلة للمقاطعة مع تقليل الحركة، لا نصوص هندسية في UI. لا بداية تنفيذ دون قراءة الذاكرة والبنية والخطة والكود.

## أين يوجد العمل؟

main: التخطيط عند ca4776f5f9bdf7cdac757daddb036a9722776c22. التطبيق على feat/android-foundation، PR #1؛ أساس الكود 0ac337e6507a04ab672190a7f3eb21ffe97cba69. افحص الفروع وPRs قبل الحكم من main وحده. لا تنشئ التطبيق مجددًا ولا تستبدل الفرع.

## ما نُفّذ

خمس وحدات: app، core/model، core/telegram، core/data، core/designsystem. Compose وNavigation3 محفوظ، ثيمات ومظهر وأيقونة أصلية، DataStore للأسماء والترتيب والتفضيلات، نموذج رسائل وأفعال بعزل chat/account/revision، renderer ومعاينة محلية صريحة. يمكن فتح bookmark خارج التطبيق في Telegram.

الأسماء محلية غير متحقق من هويتها. PreviewGateway محتوى مصطنع مستقل وليس TDLib. SendText/OpenUrl لا يعنيان إرسالًا حيًا. المسودة والتمرير مؤقتان. لا تسجيل دخول أو اتصال حقيقي مختبر.

استئناف التحقق: أضيف سكربت يحل مسار sdkmanager من SDK الفعلي، يحمي من تعارض الجذور، وينشر البيئة ويحافظ على أخطاء الرخص والتثبيت. وصل بالـworkflow مع عشرة اختبارات محاكاة.

## الأدلة الحالية

التوثيق السابق يسجل 37 core checks محلية. فحص CI للمستودع و14 زوج تباين نجح في run35284171101. البناء توقف بعدها بخطأ `sdkmanager: command not found` في job105412687739؛ Gradle tests وlint وassemble كانت skipped.

بعد الإصلاح: `bash -n scripts/install_android_sdk.sh` ناجح محليًا، و`python3 -m unittest discover -s scripts/tests -v`: 10 passed. ليست اختبارات Android. **CI الجديد لم يُتحقق بعد؛ لا APK ناجح مثبت في هذه النقطة.** لا جهاز/لقطات/FPS/Baseline Profile أو اتصال Telegram مختبر.

المصدر الأول: https://github.com/ahmed9461/BotOS/actions/runs/35284171101 . clone محلي تعذر بسبب DNS؛ أدوات GitHub تعمل. لا Android SDK محلي.

## قرارات البناء وحدود P2

Kotlin2.4.20 + AGP9.3.1 + Gradle9.6.1 + JDK21، BOM2026.09.00، Nav3 1.1.7، DataStore1.2.1. المراجع في DEPENDENCIES. لا تغيير عشوائي للإصدارات لحل PATH.

Wrapper رسمي مولد في CI ويثبت بعد مراجعته، لا bootstrap تنزيل مخصص. لا Room مكرر لقاعدة TDLib أو WorkManager لاتصال حي أو Hilt دون حاجة. P2 يحتاج TDLib commit/JNI/schema متطابقة، api_id/api_hash خاصين بالتطبيق خارج Git، حماية جلسة وموافقة واختبار حساب. لا ترسل بيانات الدخول في المحادثة أو الوثائق.

## الخطوة التالية الدقيقة

افحص run الجديد الناتج عن تحديث PR #1؛ اقرأ jobs/logs بعد انتهاء تجهيز SDK. إذا ظهر فشل جديد وثّقه في المهمة0002 قبل تغييره. لا تصف التطبيق بأنه جاهز قبل test/lint/assemble ناجحة. بعدها حدّث الذاكرة والخطتين وTESTING بدليل، ثم اختبر الواجهات على جهاز. P2 غير منجز.
