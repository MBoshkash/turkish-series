# 🎬 Turkish Series App - Project Structure

## 📁 Repository Structure

```
turkish-series/
│
├── 📁 data/                          # JSON Database
│   ├── series.json                   # قائمة كل المسلسلات
│   ├── config.json                   # إعدادات المسلسلات والمصادر
│   │
│   ├── 📁 series/                    # بيانات كل مسلسل
│   │   ├── 5127.json
│   │   ├── 5101.json
│   │   └── ...
│   │
│   └── 📁 episodes/                  # بيانات الحلقات
│       ├── 5127_01.json
│       ├── 5127_02.json
│       └── ...
│
├── 📁 scraper/                       # Python Scrapers
│   ├── main.py                       # Main scraper runner
│   ├── config.py                     # Configuration
│   │
│   ├── 📁 sources/                   # Source scrapers
│   │   ├── __init__.py
│   │   ├── base.py                   # Base scraper class
│   │   ├── akwam.py                  # أكوام - كل البيانات
│   │   ├── qissah.py                 # قصة عشق - iframe فقط
│   │   └── egybest.py                # إيجي بيست - iframe فقط
│   │
│   ├── 📁 utils/
│   │   ├── http_client.py
│   │   └── parser.py
│   │
│   └── requirements.txt
│
├── 📁 .github/
│   └── 📁 workflows/
│       ├── scrape.yml                # Auto scrape every hour
│       └── manual_scrape.yml         # Manual trigger
│
├── 📁 android/                       # Android App
│   └── TurkishSeries/
│       ├── app/
│       │   ├── src/main/
│       │   │   ├── java/.../
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── SeriesDetailActivity.kt
│       │   │   │   ├── EpisodePlayerActivity.kt
│       │   │   │   │
│       │   │   │   ├── 📁 adapters/
│       │   │   │   │   ├── SeriesAdapter.kt
│       │   │   │   │   └── EpisodeAdapter.kt
│       │   │   │   │
│       │   │   │   ├── 📁 models/
│       │   │   │   │   ├── Series.kt
│       │   │   │   │   └── Episode.kt
│       │   │   │   │
│       │   │   │   ├── 📁 api/
│       │   │   │   │   └── ApiService.kt
│       │   │   │   │
│       │   │   │   └── 📁 utils/
│       │   │   │       ├── TDMHelper.kt
│       │   │   │       └── ExoPlayerHelper.kt
│       │   │   │
│       │   │   └── res/
│       │   │       ├── layout/
│       │   │       └── values/
│       │   │
│       │   └── build.gradle
│       │
│       └── build.gradle
│
└── README.md
```

---

## 📊 JSON Data Structures

### 1. config.json - إعدادات المسلسلات والمصادر

```json
{
  "series_list": [
    {
      "id": "5127",
      "name": "المحتالون مترجم",
      "primary_source": "akwam",
      "sources": {
        "akwam": {
          "enabled": true,
          "url": "https://ak.sv/series/5127/المحتالون-مترجم",
          "fetch": ["info", "poster", "episodes", "download", "watch"]
        },
        "qissah": {
          "enabled": true,
          "url": "https://3sk.tv/series/sahtekarlar",
          "fetch": ["iframe"]
        },
        "egybest": {
          "enabled": false,
          "url": "",
          "fetch": ["iframe"]
        }
      }
    },
    {
      "id": "5101",
      "name": "المنظمة الموسم السادس",
      "primary_source": "akwam",
      "sources": {
        "akwam": {
          "enabled": true,
          "url": "https://ak.sv/series/5101/المنظمة-الموسم-السادس",
          "fetch": ["info", "poster", "episodes", "download", "watch"]
        },
        "qissah": {
          "enabled": true,
          "url": "https://3sk.tv/series/teskilat-6",
          "fetch": ["iframe"]
        }
      }
    }
  ],
  "scrape_settings": {
    "interval_minutes": 60,
    "retry_attempts": 3,
    "timeout_seconds": 30
  }
}
```

### 2. series.json - قائمة المسلسلات للعرض

```json
{
  "last_updated": "2026-01-12T15:30:00Z",
  "total": 24,
  "series": [
    {
      "id": "5127",
      "title": "المحتالون مترجم",
      "original_title": "Sahtekarlar",
      "poster": "https://ak.sv/poster/5127.jpg",
      "year": "2025",
      "rating": 8.0,
      "genres": ["دراما", "جريمة"],
      "episodes_count": 13,
      "last_episode": 13,
      "last_updated": "2026-01-12T14:00:00Z",
      "status": "ongoing"
    },
    {
      "id": "5101",
      "title": "المنظمة الموسم السادس",
      "original_title": "Teşkilat",
      "poster": "https://ak.sv/poster/5101.jpg",
      "year": "2025",
      "rating": 8.5,
      "genres": ["أكشن", "دراما"],
      "episodes_count": 45,
      "last_episode": 45,
      "last_updated": "2026-01-12T12:00:00Z",
      "status": "ongoing"
    }
  ]
}
```

### 3. series/{id}.json - تفاصيل المسلسل

```json
{
  "id": "5127",
  "title": "المحتالون مترجم",
  "original_title": "Sahtekarlar",
  "description": "محامي، كاذب، عائلة تستهلكها الرغبة في السلطة...",
  "poster": "https://ak.sv/poster/5127.jpg",
  "backdrop": "https://ak.sv/backdrop/5127.jpg",
  "year": "2025",
  "country": "تركيا",
  "rating": 8.0,
  "genres": ["دراما", "جريمة"],
  "quality": "WEB-DL 720p",
  "age_rating": "PG13",
  "cast": [
    {"name": "Burak Deniz", "role": "البطل"},
    {"name": "Haluk Bilginer", "role": ""}
  ],
  "total_episodes": 13,
  "status": "ongoing",
  "last_updated": "2026-01-12T14:00:00Z",
  "episodes": [
    {
      "number": 1,
      "title": "الحلقة 1",
      "date_added": "2025-10-14",
      "duration": "119 دقيقة"
    },
    {
      "number": 13,
      "title": "الحلقة 13",
      "date_added": "2026-01-12",
      "duration": "120 دقيقة"
    }
  ]
}
```

### 4. episodes/{series_id}_{ep}.json - بيانات الحلقة

```json
{
  "series_id": "5127",
  "series_title": "المحتالون مترجم",
  "episode_number": 13,
  "title": "الحلقة 13",
  "duration": "120 دقيقة",
  "quality": "720p",
  "file_size": "450 MB",
  "date_added": "2026-01-12",
  "last_updated": "2026-01-12T14:00:00Z",

  "servers": {
    "watch": [
      {
        "name": "أكوام",
        "type": "direct",
        "url": "https://s251d3.downet.net/.../episode13.mp4",
        "quality": "720p"
      },
      {
        "name": "قصة عشق",
        "type": "iframe",
        "url": "https://vip.3sk.tv/embed/...",
        "quality": "1080p"
      },
      {
        "name": "سيرفر 3",
        "type": "iframe",
        "url": "https://player.egybest.../embed/...",
        "quality": "720p"
      }
    ],
    "download": [
      {
        "name": "أكوام",
        "url": "https://s251d3.downet.net/.../episode13.mp4",
        "quality": "720p",
        "size": "450 MB"
      }
    ]
  },

  "screenshots": [
    "https://ak.sv/screenshots/5127_13_1.jpg",
    "https://ak.sv/screenshots/5127_13_2.jpg"
  ]
}
```

---

## 🔄 Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     GitHub Actions (كل ساعة)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   1. يقرأ config.json                                           │
│                    ↓                                             │
│   2. لكل مسلسل في القائمة:                                       │
│      ┌────────────────────────────────────────┐                 │
│      │  akwam.py  →  يسحب البيانات + روابط    │                 │
│      │  qissah.py →  يسحب iframe فقط         │                 │
│      │  egybest.py → يسحب iframe فقط         │                 │
│      └────────────────────────────────────────┘                 │
│                    ↓                                             │
│   3. يدمج كل السيرفرات في episode JSON                          │
│                    ↓                                             │
│   4. يحدث series.json بآخر الحلقات                               │
│                    ↓                                             │
│   5. Git commit + push                                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
              GitHub Pages ينشر الـ JSON كـ API
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Android App                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   GET https://username.github.io/turkish-series/data/series.json │
│                              ↓                                   │
│   عرض قائمة المسلسلات في RecyclerView                            │
│                              ↓                                   │
│   User clicks مسلسل                                              │
│                              ↓                                   │
│   GET .../data/series/5127.json                                  │
│                              ↓                                   │
│   عرض تفاصيل المسلسل + قائمة الحلقات                              │
│                              ↓                                   │
│   User clicks حلقة                                               │
│                              ↓                                   │
│   GET .../data/episodes/5127_13.json                             │
│                              ↓                                   │
│   عرض السيرفرات في ExoPlayer + زر التحميل                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## ⚙️ إضافة مسلسل جديد

### الطريقة 1: تعديل config.json يدوياً

```json
{
  "id": "NEW_ID",
  "name": "اسم المسلسل",
  "primary_source": "akwam",
  "sources": {
    "akwam": {
      "enabled": true,
      "url": "https://ak.sv/series/...",
      "fetch": ["info", "poster", "episodes", "download", "watch"]
    },
    "qissah": {
      "enabled": true,
      "url": "https://3sk.tv/series/...",
      "fetch": ["iframe"]
    }
  }
}
```

### الطريقة 2: Admin Panel (مستقبلاً)
- صفحة ويب بسيطة تضيف للـ config.json

---

## 📋 ما تحتاجه للبدء

1. **GitHub Account** (مجاني)
2. **Python 3.9+** للـ Scraper
3. **Android Studio** للتطبيق
4. **المكتبات:**
   - Python: `requests`, `beautifulsoup4`, `lxml`
   - Android: `Retrofit`, `Gson`, `ExoPlayer`, `Glide`
