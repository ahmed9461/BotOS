# موارد وسائط اصطناعية لاختبارات0007

23 سبتمبر 2026. لا ملفات من المستخدمين.

- synthetic-video.mp4: ثانية واحدة،160×90،10إطارات/ثانية،H.264 baseline/yuv420p دون صوت، مولدة منffmpeg لون ثابت.
- synthetic-sticker.webm: ثانية واحدة،64×64،10إطارات/ثانية،VP9،لون ثابت دون صوت.
- WAV: تُنشأ داخل الاختبار، صمت اصطناعي8kHz/mono/PCM16، ثانية واحدة.
- PNG وTGS: تُنشأ داخل الاختبارات؛ الصورة لون اصطناعي وTGS طبقةمتجهية بسيطة.

أوامرالفيديو:
```sh
ffmpeg -f lavfi -i 'color=c=0x7567aa:size=160x90:rate=10' -t 1 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -movflags +faststart -an synthetic-video.mp4
ffmpeg -f lavfi -i 'color=c=0x7567aa:size=64x64:rate=10' -t 1 -c:v libvpx-vp9 -b:v 32k -an synthetic-sticker.webm
```

SHA256:
- MP4:ebeb3e3ff849629ddf7c4e81c61d207b66a2d6ee3f3a5a9c81f99b4a99afdcfb
- WebM:2320450eaedae58b0796bdb9a891ba1de87341421ae764e0b1e8f0084cb0e6d2

اختبارات المشغل تثبت الاستعداد ومسارات محلية وإطلاق الموارد، ولا تثبت كل ملفات الواقع أو قياسFPS. لا تدرج بيانات حساب فيfixtures.
