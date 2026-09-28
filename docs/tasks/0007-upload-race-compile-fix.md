# 0007-D — تصحيح خطأ Kotlin في مخزن النتيجة المبكرة

23 سبتمبر 2026. هذه الخطة سبقت التصحيح، فوق `575b4156a2fffc029354cdaa066934170908d379`.

## الفشل المقروء

CI70 / 35878260581 وصل إلى تجميع `core:telegram` ثم توقف في `BotConversations.kt:286` بخطأ Kotlin
`CONCURRENT_HASH_MAP_CONTAINS_OPERATOR_ERROR`. السبب هو استخدام معامل `in` على `ConcurrentHashMap`، والذي قد يطابق `containsValue` بدل `containsKey`.

## التصحيح المحدود

- استبدال `temporaryId !in earlyUploadTerminal` بـ `!earlyUploadTerminal.containsKey(temporaryId)`.
- لا تغيير في منطق السباق أو الحدود أو عدد العناصر.
- لا بدء لشريحة التخزين قبل اجتياز هذه البوابة.
- الاختبارات الجديدة نفسها تبقى دون حذف أو تخفيف.

## القبول

نجاح تجميع واختبارات النواة الجديدة: ربط terminal update الذي يسبق response بالـsending_id دون إعادة send، ورفض recovery عند اختلاف account generation قبل أي RPC. ثم فقط تبدأ OutgoingMediaStore/Journal.
