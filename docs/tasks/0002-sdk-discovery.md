# ملحق المهمة 0002 — الحزم المنشورة ومعرف SDK

2026-09-18. الحالة: **completed**. خطة التشخيص كتبت في0ab0905a691b0e4fc897d6e5a369d15c34f53875 قبل التنفيذ. قرار التصحيح كتب في3f284f186494de9d70c99fdb644c9f69fe8892e5 قبل تغيير المعرف.

## السبب والدليل

run2 وجد sdkmanager12.0 لكنه لم يجد platforms;android-37. run3 (35287534204) حفظ artifact10524798195؛ قُرئ dependencies.json وsdk-packages.txt كاملين. أكدت Google Maven/Maven Central نشر AGP9.3.1 وKotlin2.4.20 وBOM2026.09.00 وActivity1.13.0 وLifecycle2.11.0 وNav3 1.1.7 وDataStore1.2.1 وCoroutines1.11.0.

قائمة SDK الفعلية نشرت **platforms;android-37.0 revision2**، وليس الاسم37. كانت الأدوات قادرة على قراءة القائمة؛ لا دليل يستلزم تحديثها لحل هذا الخطأ. لا نستنتج غياب Android17 عالميًا من معرف خاطئ.

## التنفيذ والتحقق

صُحح معرف حزمة التثبيت فقط في workflow وBUILD إلى37.0. compileSdk/targetSdk37 والمكتبات بقيت دون تغيير. لا symlink أو rename لمجلدSDK، ولا downgrade. run4 نجح في تجهيزSDK وتوليدWrapper وتجميع التطبيق، وrun5 (35288442618) اجتاز جميع البوابات بعد إصلاح lint المنفصل.

13 اختبارPython (10SDK و3parser) ناجحة؛ ليست بديلًا عن Androidbuild. دليل نشر الحزم هو metadata الشبكي الفعلي، ودليل التوافق العملي هو نجاح تجميع النسخة المحددة لا مجرد وجود الحزمة.

[دليل التشخيص](https://github.com/ahmed9461/BotOS/actions/runs/35287534204) · [النتيجة](https://github.com/ahmed9461/BotOS/actions/runs/35288442618) · [sdkmanager](https://developer.android.com/tools/sdkmanager).
