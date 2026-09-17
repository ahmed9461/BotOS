# ملحق المهمة0002 — فحص الحزم المنشورة قبل أي تغيير إصدار

2026-09-18، planned قبل تعديل workflow. يعتمد على tasks/0002-ci-recovery.md.

## الدليل الجديد

Run35287275281 عند ca1ac533729ea543e282a690336fb8de0b415c1a: عشرة اختبارات SDK وفحص التوثيق والتباين14 نجحت. إصلاح PATH نجح؛ sdkmanager موجود فعليًا في /usr/local/lib/android/sdk/cmdline-tools/latest/bin/sdkmanager ويعلن 12.0. الرخص نجحت. فشل التثبيت بعدها: Failed to find package 'platforms;android-37'. لم يبدأ Gradle. المصدر: https://github.com/ahmed9461/BotOS/actions/runs/35287275281 . لا يكفي هذا وحده للحكم بعدم وجود Android17 عالميًا.

## خطة التحقيق المرتبة

1. أضف جمعًا تشخيصيًا لقائمة SDK stable الفعلية، وإصدارات أدواته المتاحة.
2. اقرأ metadata الرسمية من Google Maven وMaven Central للإصدارات المثبتة في catalog؛ سجل وجودها وآخر الإصدارات المستقرة المرصودة دون تعديلها آليًا. الهدف التمييز بين توفر الحزمة ومعلومات صفحات التوثيق.
3. احفظ التشخيص artifact حتى لو فشل التثبيت. لا تخف أخطاء build أو تنتقل إلى إصدار غير مثبت.
4. اقرأ نتائج CI. عند الحاجة لرفع Command-Line Tools أو اختيار مجموعة مختلفة، سجل القرار والدليل والتوافق قبل تنفيذ التغيير.
5. حدّث الذاكرة وسجل التغييرات والاختبارات. لا ادعاء APK قبل test/lint/assemble ناجحة.

## الحدود

.github/workflows/android.yml، scripts/probe_dependencies.py، docs/PROJECT_MEMORY.md، docs/CHANGELOG.md، هذا الملحق. لا تعديل التطبيق أو مكتباته في خطوة التشخيص. بيانات التشخيص إصدارات ومصادر عامة فقط؛ لا env dump أو أسرار. الاختبارات المطلوبة: Python syntax وXML fixtures محلية؛ نتائج الشبكة تتحقق في CI.

مرجع sdkmanager: https://developer.android.com/tools/sdkmanager
مرجع Android17/SDK37: https://developer.android.com/about/versions/17/setup-sdk
مصادر artifacts: https://dl.google.com/dl/android/maven2/ ، https://repo.maven.apache.org/maven2/
