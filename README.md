# 🎬 Turkish Series App

تطبيق لمشاهدة وتحميل المسلسلات التركية من مصادر متعددة.

## 🏗️ هيكل المشروع

```
turkish-series/
├── 📁 data/                     # JSON Database
│   ├── config.json              # إعدادات المسلسلات والمصادر
│   ├── series.json              # قائمة كل المسلسلات
│   ├── 📁 series/               # بيانات كل مسلسل
│   └── 📁 episodes/             # بيانات الحلقات
│
├── 📁 scraper/                  # Python Scrapers
│   ├── main.py                  # Main runner
│   ├── requirements.txt
│   └── 📁 sources/              # Source scrapers
│       ├── akwam.py             # أكوام (كل البيانات)
│       └── qissah.py            # قصة عشق (iframe)
│
├── 📁 .github/workflows/        # GitHub Actions
│   └── scrape.yml               # Auto scrape كل ساعة
│
└── 📁 android/                  # Android App (قريباً)
```

## ⚙️ الإعداد

### 1. Clone the repository
```bash
git clone https://github.com/YOUR_USERNAME/turkish-series.git
cd turkish-series
```

### 2. Install dependencies
```bash
pip install -r scraper/requirements.txt
```

### 3. Run scraper locally
```bash
# سحب كل المسلسلات
python scraper/main.py --all

# سحب مسلسل واحد
python scraper/main.py --series 5127
```

## 📝 إضافة مسلسل جديد

### 1. أضف المسلسل في `data/config.json`:

```json
{
  "id": "5127",
  "name": "المحتالون مترجم",
  "enabled": true,
  "sources": {
    "akwam": {
      "url": "https://ak.sv/series/5127/المحتالون-مترجم",
      "fetch": ["info", "poster", "episodes", "download", "watch"]
    },
    "qissah": {
      "series_url": "https://aa.3ick.net/watch/tvshows/serie-xxx/",
      "episodes": {
        "1": "https://aa.3ick.net/watch/episodes/serie-xxx-episode-1/",
        "2": "https://aa.3ick.net/watch/episodes/serie-xxx-episode-2/"
      }
    }
  }
}
```

### 2. شغّل السكرابر
```bash
python scraper/main.py --series 5127
```

## 🔄 التحديث التلقائي

- GitHub Actions بيشتغل **كل ساعة** تلقائياً
- أو عند تحديث `config.json`
- أو يدوياً من tab الـ Actions

## 📱 API Endpoints (GitHub Pages)

بعد تفعيل GitHub Pages:

```
GET /data/series.json           → قائمة المسلسلات
GET /data/series/{id}.json      → بيانات مسلسل
GET /data/episodes/{id}_{ep}.json → بيانات حلقة
```

## 📌 ملاحظات

### بخصوص قصة عشق:
- الموقع عنده حماية قوية ضد السكرابينج
- الروابط تُضاف **يدوياً** في config.json
- هيتم فتحها في WebView في التطبيق

### بخصوص أكوام:
- السحب تلقائي بالكامل
- يسحب: البيانات، الصور، الحلقات، روابط التحميل والمشاهدة

## 🚀 الخطوات القادمة

- [ ] بناء تطبيق Android
- [ ] إضافة ExoPlayer للمشاهدة
- [ ] التكامل مع TDM للتحميل
- [ ] إضافة مصادر جديدة (إيجي بيست، إلخ)

---

📧 للمساعدة أو الاقتراحات، افتح Issue جديد.
