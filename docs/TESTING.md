# الاختبارات والأدلة

## المعاينة المختبرة — 2026-09-18

الكود: `7724a5fbc1459aac58b8d829f6e4ded1b8281f71` على feat/android-foundation. [Run35288442618](https://github.com/ahmed9461/BotOS/actions/runs/35288442618) نجح بالكامل. التوثيق المضاف بعده لا يعد تغييرًا في الكود المختبر.

| الفحص | الدليل والنتيجة |
|---|---|
| بنية المستودع وXML والإصدارات وتزامن التوثيق | ناجح فيCI |
| Python | 13 ناجحة: 10 محاكاة لتجهيز SDK و3 parser للـmetadata |
| تباين الألوان | 14 زوجًا ناجحًا؛ >=4.5:1 للنص العادي في الأزواج المحددة |
| JUnit للنواة | حالة جامعة واحدة، تضم37 assertion؛ 0 فشل/خطأ/تخطي |
| JUnit للمعاينة | 3 حالات؛ 0 فشل/خطأ/تخطي |
| Android lint | 0 أخطاء، 9 تحذيرات؛ لم تُعطل القواعد أو تُخفَ النتائج |
| assembleDebug ورفع APK | ناجحان |
| Wrapper الرسمي | تولد واستُخدم بنجاح، ومرفق كحزمة منفصلة |

قُرئت تقارير JUnit XML وlint XML من **BotOS-checks-5 / artifact10526005208**؛ بصمة حزمة الأدلة:
`a6abfe6ea3ae3fd25156494698f6d72f4e3e753f64d5d949d7502c96308b7074`.
لا تخلط37 assertion بعدد حالات JUnit الأربع. اختبار parser ليس اختبار شبكة؛ نشر الاعتمادات تحقق من metadata الرسمي فعليًا داخلCI.

## APK القابل للتتبع

**BotOS-0.1.0-preview.apk**، الحجم12,910,987 بايت، debug preview، artifact10525521304 / BotOS-preview-5. SHA256:
`b33a1093f52d0d6bb49ecfac88e05284d7b604cc3caa9c75330bf92fe526516b`.

نُزل ZIP وأُكدت بصمته (`7ba151c5adf0a197aef9d6e2df02239e55301dfe22aaea0feeccbaec30ef06b3`)، ثم استُخرج APK وقورنت بصمته مع SHA256SUMS وتحقق CRC ووجود AndroidManifest وDEX. لا يعني ذلك فحص برمجيات خبيثة أو تشغيلًا على جهاز. لا مفتاح توقيع إنتاج للمالك في هذه الشريحة.

[حزمة APK](https://github.com/ahmed9461/BotOS/actions/runs/35288442618/artifacts/10525521304) · [حزمة الأدلة](https://github.com/ahmed9461/BotOS/actions/runs/35288442618/artifacts/10526005208) · [Wrapper المولد](https://github.com/ahmed9461/BotOS/actions/runs/35288442618/artifacts/10525391589). حزمCI مؤقتة؛ تاريخ الانتهاء المعلن لهذه الحزم2026-10-01.

## التحذيرات التسعة والمتابعة

3 إشعارات توفر تحديثات لـGradle/AGP: تُراجع مع مصفوفة التوافق لا بترقية عشوائية. تحذير localeConfig خاص بـAPI33+، وقواعد dataExtractionRules، ومجلد أيقونةv26 زائد معmin26، ونص empty_bots غير مستخدم، وأيقونة monochrome مفقودة، واقتراح String.toUri. لا ندعي lint بلا تحذيرات. قواعد النسخ/نقل البيانات يجب إكمالها قبل الاحتفاظ بجلسة Telegram، ومراجعة الأيقونة قبل الإنتاج.

## سجل الوصول إلى النجاح

- run35284171101: فشل PATH قبل Gradle.
- run35287275281: إصلاح PATH نجح؛ فشل معرف حزمةSDK.
- run35287534204: تشخيص نشر الاعتمادات وقائمةSDK أثبت الحزمة37.0، دون تخفيض الإصدارات.
- run35287754553: JUnit وassemble نجحت؛ lint فشل بخطأين في موارد اللغة ومنع تسليم APK. قُرئت حزمة10525415332.
- run35288442618: الإصلاح اجتاز lint وبقية البوابات، وخرجت الحزم أعلاه.

## الأوامر

```sh
python3 scripts/check_repo.py --base <base-commit>
python3 scripts/check_contrast.py
bash -n scripts/install_android_sdk.sh
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug
```

راجع BUILD لتوليد Wrapper بالأداة الرسمية. الاختبارات المحلية لا تستبدلCI Android؛ لم يكن SDK متاحًا في الحاوية المحلية.

## ما لم يُختبر بعد

لم يُشغل التطبيق على هاتف أو محاكي، ولم تُراجع screenshots أو FPS أو Baseline Profiles أو TalkBack. يلزم اختبار: إضافة/تعديل/ترتيب/حذف bookmark، إعادة التشغيل وحفظ التفضيلات، الثيمات وتقليل الحركة، رجوع المحرر والضغط المتكرر، تغيير اللغة أثناء إشعار، الجداول والتفاصيل والأزرار، RTL/English وخط200% وشاشة صغيرة. **نجاح APK لا يثبت سلاسة الواجهة.**

لا TDLib JNI أو تسجيل دخول أو نص/وسائط/streaming حقيقية في هذه المعاينة. لا تعد اختبارات PreviewGateway اختبارات Telegram.
