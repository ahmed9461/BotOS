# ذاكرة BotOS — نقطة الاستئناف

2026-09-18. المهمة النشطة: [0002-ci-recovery](tasks/0002-ci-recovery.md)، مع [ملحق تحقيق توفر SDK](tasks/0002-sdk-discovery.md)، validating. تستكمل بوابة [0001-foundation](tasks/0001-foundation.md).

## المنتج والمتطلبات الثابتة

Android يجمع بوتات Telegram المختارة في تبويبات قابلة للتسمية والترتيب، ويعرض الرسائل والأزرار حسب أنواعها دون تكامل خاص بكل بوت أو AI يخمن callbacks. إضافة بوت بنوع مدعوم لا تتطلب تحديث APK؛ نوع جديد قد يتطلب تحديث المحرك.

عربي/RTL وEnglish/LTR، Dark/Light/System، Pearl/Graphite/Mauve لا كحلي أو أخضر، أصول أصلية ناعمة، حركة قصيرة قابلة للمقاطعة وتقليل الحركة، لا نصوص هندسية في UI. اقرأ AGENTS والذاكرة والبنية والخطة والكود قبل كل تنفيذ، ووثّق واختبر قبل التسليم.

## أين يوجد العمل؟

main عند ca4776f5f9bdf7cdac757daddb036a9722776c22 للتخطيط. التطبيق في feat/android-foundation وPR #1. أساس الكود 0ac337e6507a04ab672190a7f3eb21ffe97cba69؛ إصلاح SDK ca1ac533729ea543e282a690336fb8de0b415c1a. افحص الفروع وPRs لا main فقط. لا تعِد إنشاء التطبيق أو تمسح الفرع.

## المنفذ

خمس وحدات app/model/telegram/data/designsystem؛ Compose وNavigation3، ثيمات وأيقونة أصلية، DataStore للأسماء والترتيب والتفضيلات، نموذج رسائل وأفعال بعزل chat/account/revision، renderer ومعاينة محلية صريحة. فتح bookmark خارجي في Telegram متاح بالكود. الأسماء محلية غير متحققة؛ PreviewGateway مصطنع لا TDLib. المسودة والتمرير مؤقتان. لا دخول أو إرسال حقيقي.

إصلاح اكتشاف sdkmanager من SDK الفعلي مع فحص تعارض الجذور وحفظ أخطاء الرخص/التثبيت؛ اختبارات محاكاة10. تشخيص metadata الاعتمادات دون تغييرها مع3 اختبارات XML، وحفظ قائمة SDK والتشخيص في artifacts حتى مع فشل البناء.

## أدلة التحقق

النواة37 مسجلة في التوثيق السابق محليًا. run35284171101 فشل PATH قبل Gradle؛ repo checks وcontrast14 نجحت. run35287275281 أثبت نجاح إصلاح PATH وSDK tests10 والتباين14، ثم فشل بسبب عدم العثور على platforms;android-37 بواسطة sdkmanager12.0. لا استنتاج أن Android17 غير موجود عالميًا؛ نجمع دليل المستودع الفعلي أولًا.

محليًا بعد إضافة التشخيص: Python unittest13 passed (10 SDK +3 metadata parsing). لا Android SDK محلي والتنزيل المباشر/DNS تعذر؛ GitHub يعمل. **لا Android tests/lint/assemble أو APK ناجح مثبت بعد.** لا اختبار جهاز أو قياس FPS أو Telegram حقيقي.

## القرارات والموانع

Kotlin2.4.20 + AGP9.3.1 + Gradle9.6.1/JDK21، BOM2026.09.00، Nav3 1.1.7، DataStore1.2.1 مرشحة موثقة لكن المجموعة لم تجتز البناء. مصادر الاختيار في DEPENDENCIES؛ ملف وصف الإصدار لا يغني عن تحقق توفر artifact. لا تخفض الإصدارات أو تستخدم preview دون دليل وخطة.

Wrapper رسمي يولد في CI ويثبت بعد المراجعة. لا Room مكرر لرسائل TDLib أو WorkManager لاتصال حي أو Hilt دون حاجة. P2 يحتاج commit/JNI/schema متطابقة، credentials خاصة بالتطبيق خارج Git، حماية الجلسة والموافقة واختبار الحساب. لا أكواد دخول في المحادثة أو الوثائق.

## الخطوة التالية الدقيقة

اقرأ run التالي بعد تحديث PR #1: diagnostics/dependencies.json وsdk-packages.txt. حدد هل المشكلة أدوات SDK قديمة أو حزمة غير متاحة من المصدر. سجل القرار في ملحق0002 قبل تغيير أي رقم؛ ثم أعد CI حتى test/lint/assemble فعلية. سجّل النتائج في TESTING/الذاكرة والخطة. P2 واختبار الجهاز غير منجزين.

الأدلة: https://github.com/ahmed9461/BotOS/pull/1 ، https://github.com/ahmed9461/BotOS/actions/runs/35287275281
