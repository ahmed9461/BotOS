# ذاكرة BotOS — نقطة الاستئناف

آخر تحديث: 2026-09-18. الحالة: validating. المهمة النشطة [0002-ci-recovery](tasks/0002-ci-recovery.md)، و[ملحق lint](tasks/0002-lint-recovery.md)، ضمن بوابة تحقق [0001-foundation](tasks/0001-foundation.md).

## القواعد والمنتج

اقرأ AGENTS والذاكرة والبنية والخطة والكود قبل التنفيذ. اكتب خطة مرتبة أولًا، ثم نفذ واختبر ووثّق؛ لا نجاح بلا دليل ولا إعادة بناء العمل الموجود اعتمادًا على main فقط.

BotOS تطبيق Android يجمع البوتات في تبويبات قابلة للتسمية والترتيب، ويعرض الرسائل والأزرار ديناميكيًا حسب النوع، لا AI يخمن callback ولا تكامل لكل بوت. البوت الجديد بنوع مدعوم لا يحتاج تحديث APK؛ نوع بروتوكول جديد قد يحتاج دعمًا جديدًا.

عربي/RTL وEnglish/LTR، Dark/Light/System، Pearl/Graphite/Mauve بلا كحلي أو أخضر، أيقونات أصلية ناعمة، حركة قصيرة قابلة للمقاطعة وتقليل الحركة. النصوص الهندسية في docs لا واجهة المستخدم.

## مكان العمل والمنفذ

main للتخطيط عند ca4776f5f9bdf7cdac757daddb036a9722776c22. التنفيذ على feat/android-foundation وPR#1. أساس الكود 0ac337e6507a04ab672190a7f3eb21ffe97cba69. افحص PR والفروع قبل أي استئناف.

خمس وحدات app/model/telegram/data/designsystem: Compose وNavigation3، ثيمات وأيقونة أصلية، DataStore للأسماء والترتيب والتفضيلات، نماذج رسائل وأفعال بعزل الحساب والبوت ومراجعة الرسالة، renderer ومعاينة محلية صريحة. أسماء البوتات bookmarks غير متحقق منها؛ فتح خارجي في Telegram متاح. PreviewGateway بيانات مصطنعة وليس TDLib؛ لا تسجيل دخول أو إرسال حي. المسودات والتمرير مؤقتان.

## التحقق الفعلي

run1 (35284171101): فشل PATH. run2 (35287275281): إصلاح اكتشاف SDK نجح ثم كشف معرف حزمة غير صحيح. run3 (35287534204): metadata أكدت نشر الاعتمادات الثمانية، وقائمة SDK أثبتت platforms;android-37.0 بدل platforms;android-37. تغير معرف التثبيت فقط، وبقي APIlevel37.

run4 (35287754553) عند 65fbfdf946c36f25f124c9cbbabfa9d43df74fb4: نجحت checks وPython13 وcontrast14 وSDK وWrapper وتجميع Kotlin وassembleDebug. تقارير JUnit المقروءة تثبت 4 حالات ناجحة دون تخطي/فشل (حالة جامعة تتضمن 37 assertion للنواة +3 للمعاينة). **لكن lint فشل بخطأين و9 تحذيرات؛ APK لم يُنشر.**

كتب ملحق إصلاح lint قبل Kotlin في e820da62e58ec2640c21d167a206591a17fb5687. نُفذ LocalResources+rememberUpdatedState للإشعارات وstringResource لرد المعاينة، وأزيل شرط مكرر. **نتيجة CI بعد هذا الإصلاح لم تُقرأ بعد.** لا اختبار جهاز أو أداء أو Telegram حقيقي.

## البناء والموانع

Kotlin2.4.20، AGP9.3.1، Gradle9.6.1/JDK21، BOM2026.09.00، Activity1.13.0، Lifecycle2.11.0، Nav3 1.1.7، DataStore1.2.1، Coroutines1.11.0. المصادر في DEPENDENCIES، والنشر أثبتته metadata. لا تغير الإصدارات لمجرد تحذير أحدث رقم دون مراجعة التوافق.

لا Android SDK محلي؛ CI مصدر اختبار Android. Wrapper مولد رسميًا ولم يثبت في Git بعد. لا Room مكرر لرسائل TDLib أو WorkManager لاتصال حي أو Hilt بلا حاجة.

P2 يحتاج TDLib commit/JNI/schema متطابقة، api_id/api_hash خارج Git، حماية الجلسة وقواعد backup/نقل البيانات وموافقة واختبار الحساب. لا تجمع أكواد الدخول أو جلسات قبل ذلك. بقية تحذيرات الأيقونة والموارد وقواعد backup موثقة في ملحق lint، وتحتاج مراجعة قبل الإنتاج.

## الخطوة التالية الدقيقة

اقرأ CI الجديد بعد إصلاح الموارد. يجب نجاح JUnit/lint/assemble ورفع APK وchecksum قبل تسليم المعاينة. بعدها حدّث TESTING والخطط والسجل والذاكرة وPR بالأدلة الفعلية. اختبار الجهاز والتكامل الحي مرحلتان منفصلتان لم تنجزا بعد.

المصادر: https://github.com/ahmed9461/BotOS/pull/1 ، https://github.com/ahmed9461/BotOS/actions/runs/35287754553
