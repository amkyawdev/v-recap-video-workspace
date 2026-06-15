# 🎬 V Recap - Android Video Workspace Suite

**V Recap** သည် Android OS ပေါ်တွင် Professional-grade ဗီဒီယိုများကို စိတ်တိုင်းကျ တည်းဖြတ်နိုင်ရန်၊ စာတန်းထိုး (Captions/Subtitles) များကို စိတ်ကြိုက် Font Layout ပုံစံများဖြင့် ဖန်တီးနိုင်ရန်နှင့် Multi-resolution Rendering များ ပြုလုပ်နိုင်ရန် ဖွဲ့စည်းထားသော ခေတ်မီ **Jetpack Compose Native Android Application** ဖြစ်ပါသည်။

ဤ Project သည် အလိုအလျောက် Release Builds များ ပြုလုပ်ပေးမည့် **GitHub CI/CD Actions (build-android.yml)** နှင့် အပြည့်အစုံ ချိတ်ဆက်တည်ဆောက်ထားပါသည်။

---

## ✨ Features (အဓိက လုပ်ဆောင်ချက်များ)

- **🎥 Video Timeline Editor Workspace:** ဗီဒီယိုများ၏ Timeline တစ်လျှောက် အစ/အဆုံး ဖြတ်တောက်ချိန် (Trim Start/End Range sliders) ကို စိတ်ကြိုက် ချိန်ညှိနိုင်ခြင်း။
- **✍️ Subtitle Streaming Overlay:** ဗီဒီယိုတွင် ပေါ်မည့် စာတန်းထိုးများကို ဖန်တီး၍ စာလုံးအရောင်၊ ပုံစံများနှင့် အချိန်ကို Live Screen previews ထဲတွင် တစ်ပါတည်း စစ်ဆေးနိုင်ခြင်း။
- **⚙️ Render Controller Configs:** Output ဗီဒီယိုများအတွက် Frame rate (24 FPS, 30 FPS, 60 FPS) နှင့် Resolution Format (Full HD 1920x1080, HD 1280x720, Square 1080x1080) များကို ရွေးချယ်ထုတ်ယူနိုင်ခြင်း။
- **📂 Permission Configurator:** Android System ၏ High-performance Storage Folder permissions များကို လွယ်ကူစွာ စီမံခန့်ခွဲပေးခြင်း။
- **📊 Saved Project Archive:** တည်းဖြတ်ပြီးခဲ့သော ဗီဒီယို Histories များကို ရေရှည်သိမ်းဆည်းထားပြီး လိုအပ်ပါက Re-Edit ခလုတ်တစ်ခုတည်းဖြင့် ပြန်လည်တည်းဖြတ်နိုင်ခြင်း။

---

## 🛠 Tech Stack (အသုံးပြုထားသော နည်းပညာများ)

- **UI Framework:** Kotlin & Jetpack Compose (Modern Declarative UI)
- **Design Pattern:** Material 3 Components with custom Slate Blue & Crimson Red accent design pairings
- **Testing:** Robolectric & Roborazzi Android Screenshot tests
- **CI/CD Pipeline:** Github Actions workflows (Builds `.apk` debug & Auto Creates Release tags on every push)

---

## 🚀 GitHub Actions Auto-Build Pipeline (`build-android.yml`)

ဤ Project ၏ `.github/workflows/build-android.yml` ထဲတွင် Android Build system အလိုအလျောက် အလုပ်လုပ်ရန် ပြင်ဆင်ပေးထားပါသည်။

အကယ်၍ `main` branch သို့ Code push ပြုလုပ်လျှင် သို့မဟုတ် Pull Request တင်လျှင် GitHub Actions မှ -
1. **JDK 17 Setup** ကို အလိုအလျောက် ပြင်ဆင်ပြီး Environment ကို တည်ဆောက်ပါမည်။
2. `gradlew assembleDebug` အား Run ပြီး **Debug APK** ဖိုင်အား စနစ်တကျ Compile ဆွဲထုတ်ပေးပါမည်။
3. ထွက်ရှိလာသော `.apk` ဖိုင်ကို **GitHub Artifacts** နှင့် **GitHub Release** စာမျက်နှာတွင် Version tags အသစ်ဖြင့် တိုက်ရိုက် Upload တင်ပေးသွားမည် ဖြစ်ပါသည်။

---

## 🏎 How to Run / Build Locally (စက်ထဲတွင် Build ဆွဲနည်း)

သင်၏ Local စက်ထဲတွင် Android App ကို Run ရန် သို့မဟုတ် Build ထုတ်ရန် အောက်ပါအဆင့်များကို လုပ်ဆောင်ပါ။

### Prerequisites:
- Android Studio (Ladybug သို့မဟုတ် ပိုသစ်သော version)
- JDK 17.x

### Execute Gradlew build:
```bash
# Debug APK တည်ဆောက်ရန်
./gradlew assembleDebug

# Robolectric Tests များကို Run စာမေးရန်
./gradlew test
```

ထွက်ရှိလာမည့် APK လမ်းကြောင်း:  
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Project Structure

```text
v-recap-app/
├── .github/
│   └── workflows/
│       └── build-android.yml       # အလိုအလျောက် Release လုပ်ပေးမည့် CI Workflows
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt # သေသပ်လှပသော Jetpack Compose Core Flow Screens
│   │   │   │   └── ui/theme/      # Theme setup (Color.kt, Theme.kt, Type.kt)
│   │   │   └── res/                # XML Resources, App strings
│   │   └── test/                   # Robolectric & Screenshot tests 
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

*This V Recap Suite directory layout has been fully configured and synchronized successfully with your GitHub Repository `https://github.com/amkyawdev/v-recap-video-workspace`.*
