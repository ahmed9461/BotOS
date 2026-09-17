# بناء BotOS

## المتطلبات

JDK21، Android API37، Gradle9.6.1، اتصال لتنزيل الاعتمادات. عيّن ANDROID_HOME أو sdk.dir في local.properties. لا credentials للمعاينة.

**معرف الحزمة المنشور هو platforms;android-37.0 وليس platforms;android-37.** مستوى API في compileSdk/targetSdk يبقى37. قُرئت القائمة الفعلية في run35287534204 قبل التصحيح؛ لا تخمين من اسم الإصدار ولا إعادة تسمية لمجلدات SDK. المجموعة البرمجية المختارة متاحة في Google Maven/Maven Central، لكن توفرها ليس دليل توافق أو نجاح بناء.

## تجهيز SDK على Linux وCI

```sh
export ANDROID_HOME=/absolute/path/to/your/android-sdk
bash scripts/install_android_sdk.sh "platforms;android-37.0" "build-tools;36.0.0"
```

السكربت يستخدم sdkmanager من SDK نفسه، يفحص تعارض الجذور، يصدر البيئة للخطوات التالية، ولا يخفي فشل الرخص/التثبيت. ثبّت Android SDK Command-Line Tools أولًا إن غابت؛ لا تنزيل من مصدر مجهول. https://developer.android.com/tools/sdkmanager

```sh
bash -n scripts/install_android_sdk.sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/probe_dependencies.py
```

الأولان فحص محلي ومحاكاة؛ الثالث تشخيص نشر artifacts عبر المصادر الرسمية دون تغيير الإصدارات. معرفة published=true لا تختبر بناء التطبيق.

## البناء

Kotlin2.4.20 وAGP9.3.1 وBOM2026.09.00 وبقية الأرقام في catalog. مصادر التوافق في DEPENDENCIES. لا ترقية بلا خطة واختبار.

حتى تثبيت Wrapper المولد رسميًا في Git، استخدم Gradle9.6.1 مثبتًا أو artifact BotOS-build-tools من run ناجح:

```sh
gradle --no-daemon wrapper --gradle-version 9.6.1 --distribution-type bin --gradle-distribution-sha256-sum 9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug
```

Windows: gradlew.bat بعد توليده، وSDK Manager لتجهيز المنصة. مصدر checksum: https://gradle.org/release-checksums/.

عند النجاح: app/build/outputs/apk/debug/app-debug.apk. debug معاينة وليس إصدار إنتاج بمفتاح المالك. com.ahmed9461.botos؛ لا صلاحية شبكة أو Telegram login بعد.

## ما قبل live

اقرأ SECURITY وTELEGRAM_CAPABILITIES. لا api_hash أو جلسات في Git. TDLib source commit/JNI/schema متطابقة ودخول حقيقي وحماية جلسة وموافقة المستخدم قبل الحساب.
