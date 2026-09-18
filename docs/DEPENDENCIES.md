# الإصدارات والتوافق

آخر مراجعة: 2026-09-18. **مرصود رسميًا، ومختار، وناجح في CI أوصاف مختلفة.** أرقام المجموعة الأساسية في gradle/libs.versions.toml. اجتازت هذه المجموعة بناء النسخة الأولى في 35288442618. التغييرات واختبارات الجهاز الجديدة تعاد مراجعتها في الجولة 0003.

| المكون | المختار | السبب أو الحالة |
|---|---|---|
| Kotlin وKGP ومترجم Compose | 2.4.20 | نسخ متطابقة، مع رفع KGP بالطريقة الرسمية لـAGP 9 |
| AGP | 9.3.1 | ضمن نطاق الدعم الكامل المعلن لـKGP 2.4.20؛ لم يعتمد 9.4.0 تلقائيًا |
| Gradle | 9.6.1 | يحقق حدود توافق AGP وKGP، وإصدار ثابت لا latest |
| JDK | 21 | يحقق الحد الأدنى ويتجنب مشكلة lint المعروفة في AGP 9.3 المبكر مع JDK 17 |
| Android | compile/target 37، min 26 | الحزمة الفعلية platforms;android-37.0، وأدوات البناء 36.0.0 |
| Compose BOM | 2026.09.00 | مستقر؛ واجهة التطبيق واختبار Compose من الجدول نفسه |
| Navigation 3 | 1.1.7 | مستقر، لا الإصدار المرشح 1.2 |
| Activity | 1.13.0 | المجموعة المختبرة |
| Lifecycle | 2.11.0 | المجموعة المختبرة |
| DataStore | 1.2.1 | أسماء وتفضيلات محلية فقط |
| Coroutines | 1.11.0 | المجموعة المختبرة |
| JUnit للنواة | 4.13.2 | مناسب للاختبارات القائمة؛ ليس ادعاء أنه أحدث جيل |
| AndroidX Test JUnit | 1.3.0 | أضيف في الجولة 0003، مثبت في app/build.gradle.kts |
| AndroidX Test Runner | 1.7.0 | أضيف في الجولة 0003 لاختبار النشاط والكيبورد الحقيقيين |
| محاكي اختبار الواجهة | صورة API 35 / google_apis / x86_64 | جهاز اختبار، لا خفض لمستوى target أو compile |
| TDLib | غير مثبت بعد | يلزم تثبيت commit ومطابقة JNI والمخطط في P2 |

الإصدارات الجديدة للاختبار اختيرت من إصدارات مستقرة موثقة، ولا تفرض ترقية عشوائية على اعتماديات المنتج. واجهة isImeVisible في Compose المثبت تحمل علامة ExperimentalLayoutApi؛ قبلت في دالتين محددتين فقط، وليست حزمة alpha أو تعطيلًا عامًا لفحوص الجودة.

## المصادر الرسمية

- https://kotlinlang.org/docs/releases.html
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-to-a-higher-kgp-version
- https://docs.gradle.org/9.6.1/release-notes.html
- https://gradle.org/release-checksums/
- https://developer.android.com/develop/ui/compose/bom/bom-mapping
- https://developer.android.com/jetpack/androidx/releases/navigation3
- https://developer.android.com/jetpack/androidx/releases/activity
- https://developer.android.com/jetpack/androidx/releases/lifecycle
- https://developer.android.com/jetpack/androidx/releases/datastore
- https://developer.android.com/jetpack/androidx/releases/test
- https://developer.android.com/develop/ui/compose/testing
- https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0
- https://github.com/tdlib/td/blob/master/CMakeLists.txt

## سلسلة التوريد

بصمة توزيع Gradle 9.6.1:
`9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14`.

بصمة Wrapper JAR المنشور:
`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.

يولّد CI ملفات Wrapper بالأداة الرسمية ويرفعها. إجراءات checkout وsetup-java وsetup-gradle وupload-artifact مثبتة على commit من ناشريها. لا أسرار مستخدم ولا صلاحية كتابة للمستودع في workflow. البيانات الوصفية للاعتمادات الثمانية الأساسية تفحص من Google Maven وMaven Central قبل البناء.

## الترقية

اقرأ ملاحظات الإصدار ومصفوفة التوافق ومواضع الاستخدام، ثم حدّث الخطة والقرار والمصادر واختبر. لا + أو latest أو master لاعتماد قابل للبناء. Room وHilt وWorkManager إضافات عند الحاجة لا طقوس تأسيس. تعاد مراجعة إصلاحات AGP وR8 والملفات الأصلية قبل الإصدار الحي.
