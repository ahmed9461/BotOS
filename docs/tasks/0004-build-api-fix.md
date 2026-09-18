# ملحق0004 — تصحيح تسجيل مصادر JNI في AGP9

2026-09-18، planned قبل التعديل. المحاولة35374736478 نجحت في بناء/استعادة المعماريتين والتحقق من source contract والأيقونة والتباين، ثم فشلت أثناء تهيئة Gradle بسبب ClassCastException في core/tdlib/build.gradle.kts عند استعمال AndroidLibrarySourceSet القديم. لم تصل إلى اختبارات الجهاز.

الخطة: استبدال sourceSets القديم بـandroidComponents.onVariants وvariant.sources.jniLibs.addStaticSourceDirectory من API9.3 الرسمي، وتسجيل المسارات الموجودة فقط لتبقى معاينة app قابلة للبناء دون native. عند بناء core:tdlib نفسها يجب أن تفشل preBuild بوضوح إن غابت مكتبات المعماريتين؛ لا AAR ناقصة بصمت. لا تخفيض إصدار أو تعطيل lint أو حذف وحدة/اختبار لإخفاء الخطأ.

القبول: تنجح تهيئة Gradle للمعاينة وللمحرك، ثم تمر اختبارات JVM والجهاز ومراجعة AAR الفعلية. لا تغيير للألوان أو الأيقونة أو البيانات. التراجع بتعقب commit التصحيح. تحديث الأدلة في ملحق native-verification والذاكرة والسجل بعد قراءة النتيجة.

المراجع المقروءة: https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/Sources وhttps://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/SourceDirectories.Flat.
