# 0005 — مراجعة مسار تخزين إذن الحساب

2026-09-18. planned قبل التصحيح، فوق fe7604f3. بوابة29 ما زالت تختبر شريحة الحساب. هذه مراجعة مصدر مستقلة، لا إعادة تفسير لنتيجة لم تقرأ.

## الملاحظة المثبتة

AccountRestoreStore يسلم scope الممرر كما هو إلى PreferenceDataStoreFactory، بينما BotOsApplication يمرر scope من Dispatchers.Main.immediate. توثيق Android يحدد أن هذا scope ينفذ عمليات الإدخال والإخراج والتحويلات. إبقاء ذلك قد يضع I/O التخزين على مسار الرسم؛ لا ننتظر شكوى أداء لمعالجته، ولا ندعي أن قياس FPS أثبت تعليقًا.

## الخطة

1. اجعل AccountRestoreStore يشتق scope من سياق المالك نفسه مع Dispatchers.IO، محتفظًا بالـJob للإلغاء؛ لا SupervisorJob منفصل يتسرب بعد توقف الاختبار.
2. أضف فحص جهاز يستدعي المخزن من Dispatchers.Main ويثبت بقاء الحفظ/القراءة والاستعادة صحيحة؛ الفحص يقيس صحة العمل من مستهلك UI، وفصل I/O يراجع من السياق الصريح، وليس ادعاء قياس زمن إطار.
3. حافظ على الملف والمفتاح والافتراضي وواجهات الحساب. لا تغيير للأيقونة أو التخطيط أو أسرار البناء.
4. اقرأ نتيجة29، أصلح أي فشل فعلي بوثيقة قبل إصلاحه، ثم أعد جميع الاختبارات المطلوبة مع حالة المخزن الجديدة. حدث عدد الاختبارات وبوابة الأدلة والتوثيق، ولا تغير أي assertion سابق.

## القبول

مخزن الإذن لا يرث dispatcher الرسم لعمليات DataStore، ويظل تابعًا للمالك. القراءة/الكتابة من Main صحيحة على الجهاز. تبقى حالات الحساب/الواجهة والمحرك مطلوبة. لا نجاح اختبارات قبل XML فعلي.

المراجع الرسمية: https://developer.android.com/reference/androidx/datastore/preferences/core/PreferenceDataStoreFactory وhttps://developer.android.com/reference/kotlin/androidx/datastore/preferences/core/PreferenceDataStoreFactory .
