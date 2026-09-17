# BotOS

**بوتاتك، بطريقتك.** مساحة Android عربية بهوية لؤلؤية/فحمية ولمسات موف، وتبويبات يختار المستخدم أسماءها.

## الحالة الحالية

**0.1.0-preview مبنية ومتحققة آليًا.** شريحة محلية لتجربة الواجهة وتنظيم أسماء البوتات ومحرك المحتوى. **لا تسجيل دخولTelegram ولا إرسال حقيقي ولا TDLib JNI بعد.** الأسماء المحفوظة bookmarks غير متحقق من كونها بوتات. المعاينة توضح أنها غير متصلة، ولا تطلب رمز دخول.

الموجود: System/Light/Dark وتقليل الحركة، المساحة والمظهر، إضافة/تعديل/ترتيب/حذف تبويبات وحفظ محلي، فتح رابط البوت خارجيًا، وعرض نماذج نص وجدول واقتباس وقائمة وتفاصيل قابلة للطي وأزرار تغير الرسالة نفسها. هذا renderer داخلي تمهيدي، وليس دعمًا مكتملًا لكل Telegram Rich Messages.

## ابدأ كل مهمة من هنا

[AGENTS الإلزامي](AGENTS.md) → [ذاكرة المشروع ونقطة الوصول](docs/PROJECT_MEMORY.md) → [البنية](docs/ARCHITECTURE.md) → [الخارطة](docs/ROADMAP.md) → خطة المهمة المحددة والكود المتأثر.

اقرأ وافهم، اكتب خطة قبل التنفيذ، اتبع ترتيبها، اختبر ووثّق. لا تبدأ من جديد اعتمادًا على main فقط: التطبيق موجود على فرعfeat/android-foundation في [PR#1](https://github.com/ahmed9461/BotOS/pull/1) حتى الدمج. CI يراجع تحديث الذاكرة والسجل والخطة عند تغيير الكود؛ لا يستطيع إثبات فهم المنفذ لها، لذا تبقى المراجعة ضرورية.

## المعاينة والدليل

[Run35288442618 الناجح](https://github.com/ahmed9461/BotOS/actions/runs/35288442618) اختبر الكود7724a5fbc1459aac58b8d829f6e4ded1b8281f71: JUnit4 وPython13 وcontrast14 وlint0 أخطاء/9 تحذيرات وassembleDebug. **لم تختبر الواجهة على جهاز بعد؛ لا قياس سلاسة أو اتصالحي.**

[APK المعاينة](https://github.com/ahmed9461/BotOS/actions/runs/35288442618/artifacts/10525521304) · [التقارير](https://github.com/ahmed9461/BotOS/actions/runs/35288442618/artifacts/10526005208) · [Wrapper المولد](https://github.com/ahmed9461/BotOS/actions/runs/35288442618/artifacts/10525391589).

الحزم مؤقتة حتى2026-10-01 وفق بياناتCI. APK debug بحجم12,910,987 بايت، بصمةSHA256:
`b33a1093f52d0d6bb49ecfac88e05284d7b604cc3caa9c75330bf92fe526516b`.

## الوثائق والبناء

[قواعد العمل](docs/WORKFLOW_RULES.md) · [التصميم](docs/UI_GUIDELINES.md) · [القرارات](docs/DECISIONS.md) · [الإصدارات ومصادرها](docs/DEPENDENCIES.md) · [الأمان](docs/SECURITY.md) · [قدراتTelegram](docs/TELEGRAM_CAPABILITIES.md) · [البناء](docs/BUILD.md) · [الاختبارات والحدود](docs/TESTING.md) · [التغييرات](docs/CHANGELOG.md).

JDK21 وGradle9.6.1 وحزمةSDK platforms;android-37.0. راجع BUILD لتجهيزSDK وتوليد Wrapper الرسمي قبل استخدامه. لا توكنات أو بيانات حساب مطلوبة للمعاينة.

```sh
python3 scripts/check_repo.py
python3 scripts/check_contrast.py
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon :core:model:test :core:telegram:test :app:lintDebug :app:assembleDebug
```

## ما بعد المعاينة

مراجعة تشغيل حقيقية وثيمات وتنقل ولغة، ثم خطةP2: TDLib مثبت المصدر وحماية جلسة وقواعدbackup ودخول صريح بإذن المستخدم والتحقق من البوت والنصوص والأزرار والتحديثات. لا AI لتخمين الأفعال أو توكنات للبوتات أو خادم يحتفظ بجلسة المستخدم.
