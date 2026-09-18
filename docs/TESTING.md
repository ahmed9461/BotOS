# الاختبارات والأدلة

آخر مراجعة: 2026-09-18. المصدر المختبر الحالي: `e9b45c60bd8de80d206146c49be0288d0245e464`. هذه النتائج تخص P2a والواجهة المعتمدة، وليست دخولًا إلى حساب مستخدم.

## أحدث بوابة مكتملة

[تشغيل المحرك 35380280277](https://github.com/ahmed9461/BotOS/actions/runs/35380280277) نجح كاملًا. نُزلت حزمة `BotOS-runtime-checks-8`، رقم `10562130090`، وفُحص ZIP وطابقت بصمتها:
`35b9fb907820bc726e949b8dab0b4a5c940bda0f8e7a4b6462ee5fee9c57e9f4`.

| الفحص | النتيجة المقروءة |
|---|---|
| JVM | 21 حالة: CoreTest1، PreviewTest3، AuthorizationTest8، TdTransportTest9؛ لا فشل/خطأ/تخطي |
| JNI وKeystore على محاكي API35 | 8 حالات، لا فشل/خطأ/تخطي |
| واجهة التطبيق على المحاكي | 3 حالات، لا فشل/خطأ/تخطي؛ المدة44.988 ثانية |
| lint وحدة core:tdlib | صفر أخطاء وصفر تحذيرات |
| native-runtime.txt | version1.8.67، commit مطابق للمصدر المثبت، العربية/emoji ناجحة، waitTdlibParameters، accountUsed=false |
| اللقطات | سبع صور فعلية فُتحت للمراجعة: المظهر بثلاث حالاته، المساحة، المكتبة، المحرر، والكيبورد |
| الكيبورد | gap6.095238dp؛ rootHeight2400، imeBottom883، composerBottom1501؛ ضمن شرط -2..12dp |
| AAR | فُحص الأرشيف؛ يحتوي libtdjsonjava.so لكل من arm64-v8a وx86_64؛ ليس APK متصلًا |

[تشغيل Android المستقل 35380280304](https://github.com/ahmed9461/BotOS/actions/runs/35380280304) نجح أيضًا في tests/lint/assemble واختبارات الواجهة وجمع الأدلة وإخراج معاينة0.2. لا يثبت ذلك إرسال رسالة حقيقية من داخل التطبيق.

تفاصيل البصمات والحدود: [إغلاق P2a](tasks/0004-validation-complete.md).

## إصلاح التعليق دون تخفيف الشروط

التشغيل35377793103 على89351f43 اجتاز21JVM و8JNI/Keystore، لكن UI توقف عند0/3 حتى مهلة8دقائق. حدث مثله في Android35377793037. فُحصت حزمة10561902603 وبصمتها c3f9e92cf62db1149683ebc982866dbd38ccebfd9590e0a10a023da3b309aecb.

خطة8d25d289 سبقت تصحيحe9b45c60: إزالة runOnIdle المتداخل مع getter النشاط، وقراءة snapshot النافذة مرة واحدة عبرActivityScenario من خيط الاختبار. لا تغيير في الست تبديلات أو ظهور الكيبورد الفعلي أو شرط المسافة أو حفظ المدخلات/الإرسال. نجاح التشغيلين أعلاه ليس نتيجة تكرار الفشل حتى يظهر نجاح عشوائي؛ سبقه تعديل محدد في المزامنة. لا تغيير في تصميم الإنتاج أو الأيقونة.

## السجل السابق

0.2 على158c5258 اجتازت35294173820: أربعJVM وثلاثUI وسبع لقطات وgap6.095238dp. lint التطبيق صفر أخطاء و18تحذيرًا؛ لا تخلط ذلك مع lint المحرك0/0. شهادةdebug اختلفت عن0.1، لذلك لا نوصي بحذف نسخة صاحب الحساب أو بياناته بصمت.

0.1 على7724a5f اجتازت35288442618: Python13، contrast14، JUnit4، lint0أخطاء/9تحذيرات، وAPK. حالة CoreTest الواحدة تضم37assertion؛ ليست37حالةJUnit منفصلة. سبقها فشلPATH، ثم معرفSDK، ثم خطآن بموارد اللغة. التقارير والتفاصيل التاريخية محفوظة في Git وخطط0001 و0002، ولم تُعطل الفحوص للوصول إلى نسخة ناجحة.

## ما لم يثبت بعد

لا تسجيل دخول Telegram أو بوت حي من داخل app. لا اختبار حساب/OTP/كلمة مرور في CI. نجاحJNI/Keystore واختبارات مدخلات مصطنعة لا يستبدل تجربة صاحب الحساب. لا قياسFPS أو BaselineProfiles أو TalkBack شامل أو مصفوفة أجهزة أو runtime16KB أو اختبار شبكة ضعيفة. محاذاةELF ليست اختبار تشغيل16KB. توقيع الإنتاج الدائم وقواعد النسخ تحتاج اكتمالًا قبل إصدار متصل عام.

## أوامر التحقق

```sh
python3 scripts/check_repo.py --base <base-commit>
python3 scripts/check_contrast.py
python3 scripts/check_ui_contract.py
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_tdlib_contract.py
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --no-daemon :core:tdlib:lintDebug :core:tdlib:assembleDebug :core:tdlib:assembleDebugAndroidTest
bash scripts/run_ui_tests.sh :core:tdlib:connectedDebugAndroidTest
python3 scripts/check_device_evidence.py
python3 scripts/check_runtime_evidence.py
```

المكتبات الأصلية يجب أن تكون من نفس تشغيل البناء المثبت. Wrapper يولد رسميًا وفقBUILD. البيئة المحلية بلاAndroidSDK؛ اختبارات الجهاز المعلنة نفذت عبرActions، لا في الحاوية المحلية.
