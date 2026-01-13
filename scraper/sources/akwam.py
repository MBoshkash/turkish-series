"""Akwam Scraper - سكرابر موقع أكوام للمسلسلات التركية"""

from typing import Dict, List, Optional, Any
from bs4 import BeautifulSoup
import re
import time
import json
from urllib.parse import unquote
from .base import BaseScraper


class AkwamScraper(BaseScraper):
    """Scraper for ak.sv (Akwam) - Turkish Series Only"""

    def __init__(self):
        super().__init__()
        self.base_url = "https://ak.sv"
        self.source_name = "Akwam"
        # section=32 = المسلسلات التركية
        self.turkish_section = 32
        self.delay_between_requests = 2  # تأخير بين الطلبات (ثواني)

    def get_series_list(self, pages: int = 24) -> List[Dict[str, Any]]:
        """
        جلب قائمة المسلسلات التركية فقط من أكوام
        section=32 = المسلسلات التركية
        24 صفحة × 24 مسلسل = 576 مسلسل تقريباً
        """
        all_series = []
        seen_ids = set()

        for page in range(1, pages + 1):
            # رابط قسم المسلسلات التركية
            url = f"{self.base_url}/series?section={self.turkish_section}&page={page}"
            print(f"[Akwam] Fetching Turkish series page {page}/{pages}: {url}")

            soup = self.get_page(url)
            if not soup:
                print(f"[Akwam] Failed to get page {page}")
                time.sleep(self.delay_between_requests)
                continue

            # البحث عن روابط المسلسلات
            series_links = soup.select('a[href*="/series/"]')

            page_count = 0
            for link in series_links:
                href = link.get('href', '')

                # تجاهل روابط التصفح
                if '/series?' in href or not href:
                    continue

                # استخراج الـ ID
                id_match = re.search(r'/series/(\d+)/', href)
                if not id_match:
                    continue

                series_id = id_match.group(1)
                if series_id in seen_ids:
                    continue
                seen_ids.add(series_id)

                # استخراج الاسم من الـ URL
                name_match = re.search(r'/series/\d+/([^/]+)', href)
                name = unquote(name_match.group(1)).replace('-', ' ') if name_match else ''

                # استخراج الصورة من الكارد
                parent = link.find_parent(['div', 'article', 'li'])
                poster = ''
                if parent:
                    img = parent.select_one('img')
                    if img:
                        poster = img.get('src', '') or img.get('data-src', '')
                        # تحسين جودة الصورة
                        if poster and '/thumb/' in poster:
                            poster = re.sub(r'/thumb/\d+x\d+/', '/thumb/260x380/', poster)

                full_url = href if href.startswith('http') else f"{self.base_url}{href}"

                all_series.append({
                    'id': series_id,
                    'name': name,
                    'url': full_url,
                    'poster': poster
                })
                page_count += 1

            print(f"[Akwam] Page {page}: Found {page_count} series (Total: {len(all_series)})")

            # تأخير بين الصفحات
            time.sleep(self.delay_between_requests)

        print(f"[Akwam] Total Turkish series: {len(all_series)}")
        return all_series

    def get_series_info(self, url: str) -> Optional[Dict[str, Any]]:
        """جلب معلومات المسلسل التفصيلية"""
        soup = self.get_page(url)

        if not soup:
            return None

        # Extract series ID from URL
        match = re.search(r'/series/(\d+)/', url)
        series_id = match.group(1) if match else ""

        info = {
            'id': series_id,
            'url': url,
            'title': '',
            'original_title': '',
            'description': '',
            'poster': '',
            'backdrop': '',
            'year': '',
            'country': '',
            'language': '',
            'rating': 0.0,
            'genres': [],
            'tags': [],
            'quality': '',
            'age_rating': '',
            'duration': '',
            'cast': [],
            'total_episodes': 0,
            'status': 'ongoing',
            'episodes': []
        }

        # === TITLE ===
        h1 = soup.select_one('h1')
        if h1:
            info['title'] = h1.get_text(strip=True).split('|')[0].strip()

        if not info['title']:
            url_match = re.search(r'/series/\d+/([^/]+)', url)
            if url_match:
                info['title'] = unquote(url_match.group(1)).replace('-', ' ')

        # === METADATA من widget-body ===
        # البحث عن جدول المعلومات
        self._extract_metadata(soup, info)

        # === POSTER IMAGE ===
        self._extract_poster(soup, info)

        # === DESCRIPTION ===
        # البحث عن القصة/الوصف
        desc_selectors = [
            '.widget-body p',
            '.entry-content p',
            '[class*="story"] p',
            '.post-content p'
        ]
        for selector in desc_selectors:
            desc_elem = soup.select_one(selector)
            if desc_elem:
                text = desc_elem.get_text(strip=True)
                if len(text) > 50:  # تجاهل النصوص القصيرة
                    info['description'] = text
                    break

        # === GENRES/TAGS ===
        self._extract_tags(soup, info)

        # === EPISODES ===
        info['episodes'] = self._extract_episodes(soup)
        info['total_episodes'] = len(info['episodes'])

        return info

    def _extract_metadata(self, soup: BeautifulSoup, info: Dict):
        """استخراج الميتاداتا من صفحة المسلسل"""

        # البحث في كل النصوص
        page_text = soup.get_text()

        # === السنة ===
        # البحث عن "السنة : 2024" أو "year: 2024"
        year_patterns = [
            r'السنة\s*[:\s]+\s*(\d{4})',
            r'سنة الانتاج\s*[:\s]+\s*(\d{4})',
            r'الإنتاج\s*[:\s]+\s*(\d{4})',
            r'year\s*[:\s]+\s*(\d{4})',
        ]
        for pattern in year_patterns:
            match = re.search(pattern, page_text)
            if match:
                info['year'] = match.group(1)
                break

        # === البلد/الانتاج ===
        country_patterns = [
            r'انتاج\s*[:\s]+\s*([^\n\r]+)',
            r'الانتاج\s*[:\s]+\s*([^\n\r]+)',
            r'بلد الانتاج\s*[:\s]+\s*([^\n\r]+)',
            r'country\s*[:\s]+\s*([^\n\r]+)',
        ]
        for pattern in country_patterns:
            match = re.search(pattern, page_text, re.IGNORECASE)
            if match:
                country = match.group(1).strip()
                # تنظيف النص
                country = re.sub(r'\s+', ' ', country)
                if len(country) < 50:  # تجنب النصوص الطويلة
                    info['country'] = country.split()[0] if country else ''
                break

        # === اللغة ===
        lang_patterns = [
            r'اللغة\s*[:\s]+\s*([^\n\r]+)',
            r'language\s*[:\s]+\s*([^\n\r]+)',
        ]
        for pattern in lang_patterns:
            match = re.search(pattern, page_text, re.IGNORECASE)
            if match:
                lang = match.group(1).strip()
                if len(lang) < 30:
                    info['language'] = lang.split()[0] if lang else ''
                break

        # === الجودة ===
        quality_patterns = [
            r'الجودة\s*[:\s]+\s*([^\n\r]+)',
            r'quality\s*[:\s]+\s*([^\n\r]+)',
        ]
        for pattern in quality_patterns:
            match = re.search(pattern, page_text, re.IGNORECASE)
            if match:
                quality = match.group(1).strip()
                # استخراج الجودة فقط (مثل 720p, 1080p, WEB-DL)
                quality_match = re.search(r'(\d+p|WEB-?DL|BluRay|HDRip|DVDRip)', quality, re.IGNORECASE)
                if quality_match:
                    info['quality'] = quality_match.group(1)
                break

        # === المدة ===
        duration_patterns = [
            r'مدة المسلسل\s*[:\s]+\s*([^\n\r]+)',
            r'المدة\s*[:\s]+\s*([^\n\r]+)',
            r'duration\s*[:\s]+\s*([^\n\r]+)',
        ]
        for pattern in duration_patterns:
            match = re.search(pattern, page_text, re.IGNORECASE)
            if match:
                duration = match.group(1).strip()
                # استخراج الدقائق
                dur_match = re.search(r'(\d+)\s*دقيقة', duration)
                if dur_match:
                    info['duration'] = f"{dur_match.group(1)} دقيقة"
                else:
                    dur_match = re.search(r'(\d+)\s*min', duration, re.IGNORECASE)
                    if dur_match:
                        info['duration'] = f"{dur_match.group(1)} min"
                break

        # === التقييم من JSON-LD Schema ===
        # البحث عن AggregateRating في JSON-LD
        script_tags = soup.select('script[type="application/ld+json"]')
        for script in script_tags:
            try:
                json_text = script.get_text(strip=True)
                # قد يكون array أو object
                if json_text.startswith('['):
                    json_data = json.loads(json_text)
                    for item in json_data:
                        if isinstance(item, dict) and 'AggregateRating' in item:
                            rating_val = item['AggregateRating'].get('ratingValue')
                            if rating_val:
                                info['rating'] = float(rating_val)
                                break
                else:
                    json_data = json.loads(json_text)
                    if isinstance(json_data, dict) and 'AggregateRating' in json_data:
                        rating_val = json_data['AggregateRating'].get('ratingValue')
                        if rating_val:
                            info['rating'] = float(rating_val)
            except:
                pass

        # Fallback: البحث في النص
        if info['rating'] == 0.0:
            rating_patterns = [
                r'"ratingValue"\s*:\s*"?(\d+\.?\d*)"?',
                r'(\d+\.?\d*)\s*/\s*10',
                r'التقييم\s*[:\s]+\s*(\d+\.?\d*)',
            ]
            for pattern in rating_patterns:
                match = re.search(pattern, page_text, re.IGNORECASE)
                if match:
                    try:
                        rating = float(match.group(1))
                        if 0 < rating <= 10:
                            info['rating'] = rating
                            break
                    except:
                        pass

        # === تصنيف العمر ===
        age_patterns = [
            r'(PG-?\d+|R|G|NC-17)',
            r'للكبار فقط',
            r'عائلي',
        ]
        for pattern in age_patterns:
            match = re.search(pattern, page_text, re.IGNORECASE)
            if match:
                info['age_rating'] = match.group(0)
                break

    def _extract_poster(self, soup: BeautifulSoup, info: Dict):
        """استخراج صورة البوستر"""
        img_selectors = [
            'img[src*="img.downet.net/uploads"]',
            'img[src*="downet.net/thumb"][src*="/uploads/"]',
            'img[src*="downet.net"][src$=".webp"]',
            'img[src*="downet.net"][src$=".jpg"]',
            '.entry-image img',
            '.poster img',
            'img.img-fluid'
        ]

        for selector in img_selectors:
            imgs = soup.select(selector)
            for img in imgs:
                src = img.get('src', '') or img.get('data-src', '')
                # تجاهل الصور الافتراضية
                if src and 'placeholder' not in src.lower() and 'default' not in src.lower():
                    if '/uploads/' in src:
                        # تحسين الجودة
                        if '/thumb/32x32/' in src:
                            src = src.replace('/thumb/32x32/', '/thumb/260x380/')
                        elif '/thumb/' in src:
                            src = re.sub(r'/thumb/\d+x\d+/', '/thumb/260x380/', src)
                        info['poster'] = src
                        return

        # محاولة أخيرة
        all_imgs = soup.select('img[src*="uploads"]')
        for img in all_imgs:
            src = img.get('src', '') or img.get('data-src', '')
            if src and 'default' not in src.lower() and ('.webp' in src or '.jpg' in src):
                if '/thumb/' in src:
                    src = re.sub(r'/thumb/\d+x\d+/', '/thumb/260x380/', src)
                info['poster'] = src
                return

    def _extract_tags(self, soup: BeautifulSoup, info: Dict):
        """استخراج التاجز والأنواع"""
        # البحث عن روابط التاجز
        tag_selectors = [
            'a[href*="/tags/"]',
            'a[href*="/tag/"]',
            'a[href*="/genre/"]',
            'a[href*="/category/"]',
            '.tags a',
            '.genres a'
        ]

        tags = set()
        genres = set()

        for selector in tag_selectors:
            links = soup.select(selector)
            for link in links:
                text = link.get_text(strip=True)
                href = link.get('href', '')

                if not text or len(text) > 30:
                    continue

                # تصنيف التاجز
                if any(g in text for g in ['دراما', 'أكشن', 'كوميدي', 'رومانسي', 'غموض', 'إثارة', 'جريمة', 'عائلي', 'حرب', 'تاريخي', 'رعب', 'خيال']):
                    genres.add(text)
                else:
                    tags.add(text)

        # البحث عن badges في الصفحة
        badges = soup.select('.badge, .label, .tag-item')
        for badge in badges:
            text = badge.get_text(strip=True)
            if text and len(text) < 20:
                if any(g in text for g in ['مدبلج', 'مترجم', 'تركي', 'Netflix', 'رمضان']):
                    tags.add(text)
                elif any(g in text for g in ['دراما', 'أكشن', 'كوميدي', 'رومانسي']):
                    genres.add(text)

        info['genres'] = list(genres)[:10]
        info['tags'] = list(tags)[:10]

    def _extract_episodes(self, soup: BeautifulSoup) -> List[Dict[str, Any]]:
        """استخراج قائمة الحلقات"""
        episodes = []
        seen_episodes = set()

        episode_links = soup.select('a[href*="/episode/"]')

        for link in episode_links:
            href = link.get('href', '')

            # استخراج ID الحلقة
            id_match = re.search(r'/episode/(\d+)/', href)

            # استخراج رقم الحلقة
            ep_num_match = re.search(r'[/-](\d+)/?$', href)
            if not ep_num_match:
                ep_num_match = re.search(r'الحلقة-?(\d+)', unquote(href))

            if id_match and ep_num_match:
                episode_id = id_match.group(1)
                ep_num = int(ep_num_match.group(1))

                if ep_num not in seen_episodes and ep_num < 1000:
                    seen_episodes.add(ep_num)

                    # استخراج التاريخ
                    parent = link.find_parent(['div', 'li', 'article'])
                    date_elem = parent.select_one('[class*="date"], time, .meta') if parent else None
                    date_added = date_elem.get_text(strip=True) if date_elem else ''

                    full_url = href if href.startswith('http') else f"{self.base_url}{href}"

                    episodes.append({
                        'id': episode_id,
                        'number': ep_num,
                        'title': f'الحلقة {ep_num}',
                        'url': full_url,
                        'date_added': date_added
                    })

        episodes.sort(key=lambda x: x['number'])
        return episodes

    def get_episode_servers(self, episode_url: str) -> Dict[str, Any]:
        """نحفظ رابط صفحة الحلقة - التطبيق هيعمل resolve وقت التشغيل"""
        return {
            'watch': [{
                'name': 'أكوام',
                'type': 'akwam',
                'url': episode_url,
                'quality': '720p'
            }],
            'download': [{
                'name': 'أكوام',
                'type': 'akwam',
                'url': episode_url,
                'quality': '720p'
            }]
        }

    def get_episodes_list(self, url: str) -> List[Dict[str, Any]]:
        """جلب قائمة الحلقات من صفحة المسلسل"""
        soup = self.get_page(url)
        if not soup:
            return []
        return self._extract_episodes(soup)
