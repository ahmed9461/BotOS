# سجل الإصدارات والتوافق

تاريخ المراجعة: 2026-09-18. ثلاثة أوصاف مختلفة: **مرصود رسميًا**، **مختار للبناء**، **ناجح في CI**. لا تساوِ بينها. ستثبت الأرقام الفعلية في `gradle/libs.versions.toml` عند إنشاء البناء.

| المكوّن | المرصود من المصدر الرسمي | قرار الشريحة |
|---|---|---|
| Android Gradle Plugin | 9.4.0 | يستلزم Gradle >=9.6.0، JDK >=17، يدعم API 37؛ التوافق سيختبر |
| Gradle | 9.6.1 patch موثق لسلسلة الحد الأدنى | مختار مبدئيًا بدل 9.6.0 بسبب إصلاحاتها؛ ليس ادعاء بأنه أحدث إصدار عالمي |
| Kotlin | 2.4.20 | compiler/plugin متطابقان؛ AGP9 built-in Kotlin مع رفع KGP بالطريقة الرسمية |
| Compose | BOM سبتمبر 2026 في mapping | اعتماد BOM stable، لا خلط إصدارات يدويًا |
| Navigation 3 | stable 1.1.7، RC 1.2.0-rc01 | نختار stable فقط |
| Activity | stable 1.13.0 | مرشح |
| Lifecycle | stable 2.11.0 | مرشح |
| DataStore | stable 1.2.1 | مرشح |
| Coroutines | 1.11.0 | مرشح |
| TDLib | المصدر الحالي يعلن 1.8.67 | ليس اعتماد Maven مفترضًا؛ تثبيت commit وبناء JNI في P2 |

## مصادر القرار

- AGP وتوافقه: https://developer.android.com/build/releases/agp-9-4-0-release-notes
- رفع KGP مع built-in Kotlin: https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-to-a-higher-kgp-version
- Gradle patch: https://docs.gradle.org/9.6.1/release-notes.html
- Kotlin: https://kotlinlang.org/docs/releases.html
- توافق Kotlin/Gradle/AGP: https://kotlinlang.org/docs/gradle-configure-project.html
- BOM: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Navigation 3: https://developer.android.com/jetpack/androidx/releases/navigation3
- Activity: https://developer.android.com/jetpack/androidx/releases/activity
- Lifecycle: https://developer.android.com/jetpack/androidx/releases/lifecycle
- DataStore: https://developer.android.com/jetpack/androidx/releases/datastore
- Coroutines: https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0

## إعادة التحقق

عند أي ترقية اقرأ release notes ومصفوفة التوافق ومواضع الاستخدام، سجل السبب والمخاطر والاختبارات وخطة الرجوع. لا تستخدم +/latest/master لاعتماد قابل للبناء. لا تضف Room/WorkManager/Hilt لمجرد ورودها في اقتراح سابق. لا تسجل نجاح بناء قبل وجود run مكتمل.
