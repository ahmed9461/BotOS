# بناء BotOS

## المتطلبات

JDK21، Android SDK platform37 وbuild-tools36.0.0، Gradle9.6.1، اتصال لتنزيل الاعتمادات. ضع sdk.dir في local.properties أو عيّن ANDROID_HOME. لا credentials مطلوبة للمعاينة.

## تجهيز SDK على Linux وCI

أداة sdkmanager جزء من Android SDK Command-Line Tools وليست مضمونة في PATH. شغّل:

```sh
export ANDROID_HOME=/absolute/path/to/your/android-sdk
bash scripts/install_android_sdk.sh "platforms;android-37" "build-tools;36.0.0"
```

السكربت لا ينزل executable من مصدر مجهول: يستخدم الأداة المثبتة داخل الجذر المحدد، ثم أدوات الإصدارات المثبتة إن لم يوجد مسار latest. latest هنا اسم مجلد محلي شائع وليس اعتماد Gradle متحركًا. يطبع إصدار الأداة، يتحقق من عدم تعارض ANDROID_HOME وANDROID_SDK_ROOT، ويصدرهما ومسار الأدوات للخطوات اللاحقة في GitHub. يقبل رخص SDK المطلوبة أثناء التجهيز؛ يفشل بوضوح إن لم توجد الأدوات أو فشلت الرخص/الحزم. ثبّت Android SDK Command-Line Tools أولًا عند غيابها. مرجع التثبيت والحزم: https://developer.android.com/tools/sdkmanager

اختبارات التجهيز دون Android أو شبكة:

```sh
bash -n scripts/install_android_sdk.sh
python3 -m unittest discover -s scripts/tests -v
```

## التوافق

AGP9.3.1 ضمن حد دعم Kotlin2.4.20 الكامل المعلن؛ AGP9.4.0 الأحدث خارج ذلك الحد وقت الاختيار. JDK21 لتوافق أدوات البناء الحالية. المراجع في DEPENDENCIES.md. لا نجاح بناء مفترض قبل انتهاء CI.

## البناء الأول

CI يستخدم إجراء Gradle الرسمي المثبت على commit ويولّد Wrapper بواسطة Gradle نفسه. إلى أن تُثبت ملفات Wrapper المولدة في المستودع، استخدم Gradle9.6.1 محليًا أو حزمة BotOS-build-tools من run ناجح:

```sh
gradle --no-daemon wrapper --gradle-version 9.6.1 --distribution-type bin --gradle-distribution-sha256-sum 9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug
```

في Windows استخدم gradlew.bat بعد توليده وAndroid Studio SDK Manager لتجهيز المنصة. checksum من https://gradle.org/release-checksums/.

المخرجات عند النجاح: app/build/outputs/apk/debug/app-debug.apk. debug للمعاينة لا إصدار إنتاج موقع بمفتاح المالك. الحزمة com.ahmed9461.botos. لا صلاحية شبكة أو دخول في هذه الشريحة.

## قبل الاتصال الحقيقي

اقرأ SECURITY وTELEGRAM_CAPABILITIES. لا api_hash أو جلسات في Git. يلزم source commit ثابت وبناء JNI ومطابقة schema ودخول حقيقي وموافقة قبل اختبار الحساب.
