# بناء BotOS

## المتطلبات

JDK 21، Android SDK platform 37 وbuild-tools 36.0.0، Gradle 9.6.1، اتصال لتنزيل الاعتمادات. ضع sdk.dir في local.properties أو عيّن ANDROID_HOME. لا credentials مطلوبة للمعاينة.

## التوافق

AGP 9.3.1 ضمن الحد الأعلى الذي توثقه مصفوفة Kotlin 2.4.20. AGP9.4.0 أحدث لكن خارج الحد المعلن للدعم الكامل في جدول KGP وقت الاختيار؛ لذلك لم يُستخدم آليًا. JDK21 يتجنب مشكلة lint على JDK17 في إصدارات AGP9.3 المبكرة. المصادر في DEPENDENCIES.md. لا نجاح بناء مفترض قبل انتهاء CI.

## البناء الأول

استُبعد مشغل تنزيل مخصص؛ CI يستخدم إجراء Gradle الرسمي المثبت على commit ويولّد Wrapper بواسطة Gradle نفسه. إلى أن تُثبت ملفات الـWrapper المولدة في المستودع، استخدم تثبيت Gradle9.6.1 محليًا أو حزمة BotOS-build-tools من run ناجح:

```sh
gradle --no-daemon wrapper --gradle-version 9.6.1 --distribution-type bin --gradle-distribution-sha256-sum 9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug
```

في Windows استخدم gradlew.bat بعد توليده. checksum من https://gradle.org/release-checksums/.

المخرجات: app/build/outputs/apk/debug/app-debug.apk. نسخة debug للمعاينة لا إصدار إنتاج موقع بمفتاح المالك. التطبيق BotOS والحزمة com.ahmed9461.botos. لا صلاحية شبكة أو دخول في هذه الشريحة.

## قبل الاتصال الحقيقي

اقرأ SECURITY وTELEGRAM_CAPABILITIES. لا api_hash أو جلسات في Git. يلزم source commit ثابت وبناء JNI ومطابقة schema وتدفق دخول حقيقي وموافقة قبل اختبار الحساب.
