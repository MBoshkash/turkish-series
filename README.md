# 🎬 Turkish Series - مسلسلات تركية

تطبيق Android لمشاهدة المسلسلات التركية المترجمة للعربية. البيانات من موقع أكوام.

---

## 📋 نظرة عامة على المشروع

### ما يتكون منه المشروع:
1. **Scraper (Python)** - سكريبت لسحب بيانات المسلسلات من أكوام
2. **Data (JSON)** - قاعدة بيانات JSON مستضافة على GitHub Pages
3. **Android App (Kotlin)** - تطبيق Android للمشاهدة
4. **GitHub Actions** - تحديث تلقائي للبيانات كل 6 ساعات

---

## 🏗️ هيكل المشروع

```
turkish-series/
├── 📁 data/                          # قاعدة البيانات JSON
│   ├── series.json                   # قائمة كل المسلسلات (564 مسلسل)
│   ├── version.json                  # معلومات الإصدار للتحديث داخل التطبيق
│   ├── 📁 series/                    # بيانات كل مسلسل (JSON لكل مسلسل)
│   └── 📁 episodes/                  # بيانات الحلقات
│
├── 📁 scraper/                       # Python Scraper
│   ├── main.py                       # السكريبت الرئيسي
│   ├── requirements.txt              # المتطلبات (requests, beautifulsoup4)
│   └── 📁 sources/
│       └── akwam.py                  # سكريبت أكوام
│
├── 📁 android/                       # تطبيق Android
│   └── app/src/main/
│       ├── java/com/turkish/series/
│       │   ├── MainActivity.kt           # الشاشة الرئيسية
│       │   ├── SeriesDetailActivity.kt   # تفاصيل المسلسل
│       │   ├── EpisodePlayerActivity.kt  # مشغل الحلقات
│       │   ├── SplashActivity.kt         # شاشة البداية
│       │   ├── 📁 adapters/              # RecyclerView Adapters
│       │   ├── 📁 models/                # Data Models
│       │   └── 📁 utils/
│       │       ├── AkwamResolver.kt      # فك روابط أكوام
│       │       └── UpdateChecker.kt      # فحص التحديثات
│       └── res/                          # Resources (layouts, drawables, etc.)
│
├── 📁 .github/workflows/
│   └── scrape.yml                    # GitHub Actions للتحديث التلقائي
│
└── README.md                         # هذا الملف
```

---

## 📱 تطبيق Android

### التقنيات المستخدمة:
- **Kotlin** - لغة البرمجة
- **ViewBinding** - للتعامل مع Views
- **Retrofit + OkHttp** - للـ API calls
- **Glide** - لتحميل وتخزين الصور (DiskCacheStrategy.ALL)
- **ExoPlayer Media3** - لتشغيل الفيديو
- **Material Design 3** - للتصميم
- **ConstraintLayout** - للـ layouts

### الشاشات:
1. **SplashActivity** - شاشة بداية متحركة بثيم تركي (هلال ونجمة)
2. **MainActivity** - قائمة المسلسلات مع:
   - Pagination (30 مسلسل في المرة)
   - Auto-load عند السكرول
   - فلاتر (النوع، السنة، التقييم)
   - ترتيب (الأحدث، الأقدم، التقييم، أبجدي)
   - بحث
   - Navigation Drawer
3. **SeriesDetailActivity** - تفاصيل المسلسل وقائمة الحلقات
4. **EpisodePlayerActivity** - مشغل الفيديو مع اختيار السيرفر والجودة

### الميزات:
- RTL Layout للعربية
- خط Tajawal العربي
- Dark Theme
- تخزين الصور (Image Caching)
- "اضغط مرة أخرى للخروج"
- تحديثات إجبارية داخل التطبيق

### نظام التحديثات:
```kotlin
// UpdateChecker.kt يفحص version.json من GitHub Pages
// لو version_code أكبر من الحالي، يظهر dialog إجباري
// بيفتح المتصفح لتحميل APK من GitHub Releases
```

### فك روابط أكوام:
```kotlin
// AkwamResolver.kt يتعامل مع:
// 1. صفحة download/watch في أكوام
// 2. استخراج روابط السيرفرات
// 3. فك الروابط المشفرة للوصول للفيديو الأصلي
```

---

## 🐍 Scraper (Python)

### التشغيل:
```bash
# تثبيت المتطلبات
pip install -r scraper/requirements.txt

# سحب كل المسلسلات
cd scraper
python main.py --all

# سحب مسلسل معين
python main.py --series 5127
```

### ما يسحبه السكريبت:
- معلومات المسلسل (الاسم، الوصف، التقييم، السنة، النوع، الجودة)
- صورة البوستر
- قائمة الحلقات (رقم، عنوان، تاريخ، رابط)
- `last_episode_date` - تاريخ آخر حلقة للترتيب الصحيح

### البيانات المُنتجة:
```json
// data/series.json - قائمة مختصرة
[
  {
    "id": 5127,
    "name": "المحتالون مترجم",
    "poster": "https://...",
    "rating": "8.5",
    "year": "2024",
    "episodes_count": 25,
    "quality": "WEB-DL",
    "last_episode_date": "منذ 3 أيام"
  }
]

// data/series/5127.json - بيانات كاملة
{
  "id": 5127,
  "name": "المحتالون مترجم",
  "description": "...",
  "poster": "...",
  "genres": ["دراما", "رومانسي"],
  "episodes": [...]
}
```

---

## 🔄 GitHub Actions

### الملف: `.github/workflows/scrape.yml`

### التشغيل:
- **تلقائي**: كل 6 ساعات (cron: '0 */6 * * *')
- **يدوي**: من Actions tab → Run workflow

### المتطلبات:
1. **PAT_TOKEN** - Personal Access Token مع صلاحية `repo`
   - يُضاف في: Settings → Secrets → Actions → New repository secret
   - الاسم: `PAT_TOKEN`

### سبب استخدام PAT:
الـ GITHUB_TOKEN الافتراضي لا يستطيع عمل push عندما يكون هناك commits جديدة على الـ remote. الـ PAT يحل هذه المشكلة.

### تشغيل يدوي:
1. روح Actions tab
2. اختار "Scrape Turkish Series"
3. اضغط "Run workflow"
4. اختار branch: main
5. اضغط "Run workflow"

**مهم**: لا تستخدم "Re-run job" لأنه يشغل الكود القديم!

---

## 🌐 GitHub Pages (API)

### الإعداد:
Settings → Pages → Source: Deploy from a branch → Branch: main → Folder: / (root)

### الـ Endpoints:
```
https://mboshkash.github.io/turkish-series/data/series.json
https://mboshkash.github.io/turkish-series/data/version.json
https://mboshkash.github.io/turkish-series/data/series/{id}.json
```

---

## 📦 نظام التحديثات (In-App Updates)

### الملف: `data/version.json`
```json
{
  "version_code": 1,
  "version_name": "1.0.0",
  "apk_url": "https://github.com/MBoshkash/turkish-series/releases/download/v1.0.0/app-release.apk",
  "release_notes": "الإصدار الأول من التطبيق",
  "force_update": true
}
```

### كيف يعمل:
1. التطبيق يفحص version.json عند البداية
2. لو `version_code` أكبر من الحالي، يظهر dialog
3. لو `force_update: true`، الـ dialog لا يمكن إغلاقه
4. الضغط على "تحديث" يفتح المتصفح لتحميل APK

### إصدار تحديث جديد:
1. عدّل `version_code` و `version_name` في version.json
2. عدّل `versionCode` و `versionName` في app/build.gradle.kts
3. ابني APK جديد
4. ارفعه في GitHub Releases بنفس الـ tag
5. ادفع التغييرات

---

## 🔧 إعدادات مهمة

### Android (app/build.gradle.kts):
```kotlin
android {
    namespace = "com.turkish.series"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.turkish.series"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
}
```

### الثيم والألوان (res/values/):
- Background: #0D0D0D (أسود داكن)
- Primary: #E50914 (أحمر - للعناصر المهمة)
- Surface: #1A1A1A
- Text: #FFFFFF

### الخطوط:
- Tajawal (عربي) - محمل من Google Fonts

---

## 🐛 مشاكل معروفة وحلولها

### 1. الترتيب بالتاريخ مش شغال صح
**السبب**: كل المسلسلات كانت بنفس الـ timestamp (وقت السكرابينج)
**الحل**: أضفنا `last_episode_date` من تاريخ آخر حلقة فعلي

### 2. GitHub Actions push rejected
**السبب**: الـ GITHUB_TOKEN محدود الصلاحيات
**الحل**: استخدام PAT_TOKEN

### 3. Duplicate resources error
**السبب**: XML و PNG بنفس الاسم في mipmap
**الحل**: حذف ملفات XML للأيقونات

---

## 📝 للتطوير المستقبلي

- [ ] إضافة Favorites/Watchlist
- [ ] حفظ آخر حلقة تمت مشاهدتها
- [ ] إضافة مصادر أخرى (قصة عشق، إيجي بيست)
- [ ] Download Manager للتحميل
- [ ] Push Notifications للحلقات الجديدة
- [ ] Search history
- [ ] Filter by multiple genres

---

## 📞 التواصل

- **GitHub Issues**: للمشاكل والاقتراحات
- **Repository**: https://github.com/MBoshkash/turkish-series

---

## 📄 الترخيص

هذا المشروع للاستخدام الشخصي والتعليمي فقط.
