# ملحق المهمة 0002 — الموارد المتغيرة وفحص الجودة

2026-09-18. الحالة: **completed — إصلاح خطأي lint**. الخطة فيe820da62e58ec2640c21d167a206591a17fb5687 سبقت المصدر7724a5fbc1459aac58b8d829f6e4ded1b8281f71.

## الدليل المقروء قبل الإصلاح

run35287754553: SDK وWrapper وJUnit وassembleDebug نجحت؛ lint أبلغ خطأين و9 تحذيرات ومنع النشر. قُرئت SARIF/JUnit منartifact10525415332 وBotOsApp.kt وWorkspaceScreen.kt. الخطآن LocalContextGetResourceValueCall في قراءة إشعارحدث وردالمعاينة، وتحذيرcompiler لشرط selected != null المكرر.

## التنفيذ بالترتيب

- [x] قراءة المصدر والتقارير ومرجع LocalResources وrememberUpdatedState.
- [x] استخدام LocalResources.current مع rememberUpdatedState داخل collector، وstringResource لرد المعاينة في التركيب قبلcallback.
- [x] توضيح فرعي selected == null/else دون تغيير سلوك التنقل.
- [x] إعادةCI الكامل؛ run35288442618 نجح مع lint صفر أخطاء، JUnit4 ناجحة، وAPK منشور.
- [x] قراءة حزمة الأدلة10526005208، التحقق من APK10525521304، وتحديث الذاكرة والاختبارات والخطط.

## ما لم ندّعه

لم نضف baseline أو suppress ولم نعطل lint. بقيت9 تحذيرات مصنفة فيTESTING؛ قواعد نقل البيانات/backup يجب إكمالها قبل حفظ جلسةTelegram، وتحسينات الأيقونة قبلالإنتاج. لا اختبار تغيير لغة أثناءتشغيل علىجهاز، ولاFPS أو اتصالحي حتىالآن.

الملفات المعدلة: BotOsApp.kt وWorkspaceScreen.kt ووثائق المهمة. التراجع بcommit عكسي دون حذف التاريخ.

المصادر: [run5](https://github.com/ahmed9461/BotOS/actions/runs/35288442618) · [مصدرLocalResources](https://android.googlesource.com/platform/frameworks/support/+/0624f640fd3a47edfcf8a070f609d278fb5eb41b/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidCompositionLocals.android.kt) · [Compose side effects](https://developer.android.com/develop/ui/compose/side-effects).
