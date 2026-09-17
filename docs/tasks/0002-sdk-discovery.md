# ملحق المهمة0002 — فحص الحزم المنشورة قبل أي تغيير إصدار

2026-09-18، validating. يعتمد على tasks/0002-ci-recovery.md. خطة التحقيق كتبت أولًا في commit0ab0905a691b0e4fc897d6e5a369d15c34f53875.

## الدليل الجديد

Run35287275281 عند ca1ac533729ea543e282a690336fb8de0b415c1a: عشرة اختبارات SDK وفحص التوثيق والتباين14 نجحت. إصلاح PATH نجح؛ sdkmanager في /usr/local/lib/android/sdk/cmdline-tools/latest/bin/sdkmanager يعلن12.0. الرخص نجحت. فشل التثبيت بعدها: Failed to find package 'platforms;android-37'. لم يبدأ Gradle. https://github.com/ahmed9461/BotOS/actions/runs/35287275281 . لا يكفي هذا وحده للحكم بعدم وجود Android17 عالميًا.

## الخطة والتقدم

- [x] جمع قائمة SDK stable عند فشل التثبيت، مع الحفاظ على رمز فشل التثبيت نفسه.
- [x] قراءة metadata من Google Maven وMaven Central للإصدارات المثبتة؛ لا تعديل آلي للأرقام.
- [x] حفظ التشخيص artifact حتى مع الفشل. اختبارات Python13 نجحت محليًا (10 SDK +3 XML).
- [ ] قراءة CI والتحقق من الحزم المنشورة.
- [ ] إن احتاجت أدوات SDK رفعًا أو مجموعة بناء أخرى، سجل الدليل والتوافق قبل التغيير.
- [ ] تحديث نتائج الذاكرة والتوثيق؛ لا APK قبل test/lint/assemble ناجحة.

مصادر الفحص عامة فقط، لا env dump أو أسرار. فشل metadata يعلن unknown لا missing. اكتمال خطوة التشخيص لا يعني أن كل الاعتمادات متاحة أو أن التطبيق بُني. اختبارات XML محلية لا اختبارات شبكة.

الملفات: .github/workflows/android.yml، scripts/probe_dependencies.py، scripts/tests/test_dependency_probe.py، docs/PROJECT_MEMORY.md، docs/CHANGELOG.md، الملحق الحالي. التطبيق وإصداراته لم تتغير في التشخيص.

المراجع: https://developer.android.com/tools/sdkmanager ، https://developer.android.com/about/versions/17/setup-sdk ، https://dl.google.com/dl/android/maven2/ ، https://repo.maven.apache.org/maven2/
