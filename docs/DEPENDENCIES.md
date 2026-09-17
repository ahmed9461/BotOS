# الإصدارات والتوافق — مراجعة2026-09-18

**مرصود رسميًا ≠ مختار ≠ ناجح في CI.** المصدر التنفيذي للأرقام gradle/libs.versions.toml. المجموعة التالية مثبتة في الكود وبانتظار التحقق من بناء Android.

| المكوّن | المختار | سبب/حالة |
|---|---|---|
| Kotlin/KGP/Compose compiler | 2.4.20 | نفس النسخة، رفع KGP بالطريقة الرسمية لـAGP9 built-in Kotlin |
| AGP | 9.3.1 | ضمن النطاق الكامل لـKGP2.4.20؛ رُصد9.4.0 أحدث ولم يُعتمد آليًا |
| Gradle | 9.6.1 | >=حدAGP9.3 وهو9.5.0 و<=حدKGP وهو9.7.0؛ patch مثبت لا latest |
| JDK | 21 | >=17، يتجنب مشكلة lint علىJDK17 فيAGP9.3 المبكر |
| Android SDK | compile/target37، min26 | AGP9.3 يدعم37؛ build-tools36.0.0 |
| Compose BOM | 2026.09.00 | stable من mapping، المكتبات عبر BOM |
| Navigation3 | 1.1.7 | stable، لا1.2RC؛ للشاشات Compose |
| Activity | 1.13.0 | stable مرصود |
| Lifecycle | 2.11.0 | stable مرصود |
| DataStore | 1.2.1 | أسماء وتفضيلات محلية، لا قاعدة رسائل مكررة |
| Coroutines | 1.11.0 | إصدار موثق |
| JUnit | 4.13.2 | مناسب للمهمة الحالية؛ ليس ادعاء أنه أحدث جيل |
| TDLib | غير مُثبت في البناء بعد | مصدرmaster يعلن1.8.67؛ يجب pin commit وJNI وschema فيP2 |

## المصادر الرسمية

- https://kotlinlang.org/docs/releases.html
- https://kotlinlang.org/docs/gradle-configure-project.html — جدولKGP2.4.20: Gradle7.6.3–9.7.0، AGP8.5.2–9.3.1.
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-to-a-higher-kgp-version
- https://docs.gradle.org/9.6.1/release-notes.html
- https://gradle.org/release-checksums/
- https://developer.android.com/develop/ui/compose/bom/bom-mapping
- https://developer.android.com/jetpack/androidx/releases/navigation3
- https://developer.android.com/jetpack/androidx/releases/activity
- https://developer.android.com/jetpack/androidx/releases/lifecycle
- https://developer.android.com/jetpack/androidx/releases/datastore
- https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0
- https://github.com/tdlib/td/blob/master/CMakeLists.txt

## سلسلة التوريد

Gradle9.6.1 bin SHA256: 9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14. Wrapper JAR المنشور: 497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7. لا نرفق JAR غير متحقق. setup-gradle يولد Wrapper رسميًا ويصدره كأثر بناء.

إجراءاتCI مثبتة على commit مسترجع من مستودع كل ناشر: checkoutv5، setup-javav5، upload-artifactv4، gradle/actionsv5.0.2. لا أسرار مستخدم ولا صلاحية كتابة للمستودع فيworkflow.

## الترقية

اقرأ notes والتوافق ومواضع الاستخدام، حدّث خطة وADR ومصادر ثم اختبر. لا +/latest/master في依تماد قابل للبناء. Room/Hilt/WorkManager ليست إضافات واجبة دون حاجة. قبل إنتاجlive تعاد مراجعة patchesAGP ومشاكلR8 وnative ومتطلبات النشر.
