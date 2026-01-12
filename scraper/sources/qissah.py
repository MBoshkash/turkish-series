"""
Qissah Eshq (3ick.net) Scraper - سكرابر موقع قصة عشق
يسحب iframe فقط للمشاهدة

ملاحظة: الموقع بيغير الـ slugs باستمرار عشان الحماية
لازم نحفظ الروابط المحدثة في config.json
"""

from typing import Dict, List, Optional, Any
from bs4 import BeautifulSoup
import re
from .base import BaseScraper


class QissahScraper(BaseScraper):
    """Scraper for 3ick.net (Qissah Eshq) - iframe only"""

    # الدومينات المختلفة للموقع (بيتغيروا)
    DOMAINS = [
        "aa.3ick.net",
        "bn.3isk.ink",
        "3esk.onl",
    ]

    def __init__(self):
        super().__init__()
        self.base_url = "https://aa.3ick.net"
        self.source_name = "QissahEshq"

    def set_domain(self, domain: str):
        """Change the base domain if needed"""
        self.base_url = f"https://{domain}"

    def get_series_list(self, page: int = 1) -> List[Dict[str, Any]]:
        """Get list of all series"""
        url = f"{self.base_url}/watch/tvshows/"
        if page > 1:
            url = f"{self.base_url}/watch/tvshows/page/{page}/"

        soup = self.get_page(url)
        if not soup:
            return []

        series_list = []

        # Find series links
        links = soup.select('a[href*="/watch/tvshows/serie-"]')

        seen = set()
        for link in links:
            href = link.get('href', '')
            if href not in seen:
                seen.add(href)

                # Get title
                title_elem = link.select_one('.entry-title, h2, h3, .title')
                title = title_elem.get_text(strip=True) if title_elem else ''

                if not title:
                    # Try to get from image alt
                    img = link.select_one('img')
                    title = img.get('alt', '') if img else ''

                # Get poster
                img = link.select_one('img')
                poster = img.get('src', '') if img else ''

                # Extract slug from URL
                slug_match = re.search(r'/serie-([^/]+)/', href)
                slug = slug_match.group(1) if slug_match else ''

                series_list.append({
                    'title': title,
                    'slug': slug,
                    'url': href,
                    'poster': poster
                })

        return series_list

    def get_series_info(self, url: str) -> Optional[Dict[str, Any]]:
        """Get series information and episodes list"""
        soup = self.get_page(url)

        if not soup:
            return None

        info = {
            'title': '',
            'url': url,
            'poster': '',
            'episodes': []
        }

        # Get title
        title = self.get_text(soup, 'h1, .entry-title, title')
        info['title'] = title.split('|')[0].strip() if title else ''

        # Get poster
        poster = self.get_attr(soup, '.poster img, .thumb img, img.wp-post-image', 'src')
        info['poster'] = poster

        # Get episodes
        info['episodes'] = self._extract_episodes(soup)

        return info

    def _extract_episodes(self, soup: BeautifulSoup) -> List[Dict[str, Any]]:
        """Extract episodes list from series page"""
        episodes = []

        # Find episode links - multiple patterns
        episode_selectors = [
            'a[href*="/watch/episodes/"]',
            'a[href*="-episode-"]',
            '.episodes-list a',
            '[class*="episode"] a',
            'a[href*="season-"][href*="episode-"]'
        ]

        for selector in episode_selectors:
            links = soup.select(selector)
            if links:
                break

        seen = set()
        for link in links:
            href = link.get('href', '')

            # Extract season and episode numbers
            match = re.search(r'season-(\d+)-episode-(\d+)', href)
            if match:
                season = int(match.group(1))
                ep_num = int(match.group(2))

                key = f"s{season}e{ep_num}"
                if key not in seen:
                    seen.add(key)
                    episodes.append({
                        'season': season,
                        'number': ep_num,
                        'url': href,
                        'title': f'الموسم {season} - الحلقة {ep_num}'
                    })

        episodes.sort(key=lambda x: (x.get('season', 1), x['number']))
        return episodes

    def get_episodes_list(self, url: str) -> List[Dict[str, Any]]:
        """Get list of episodes from series page"""
        soup = self.get_page(url)
        if not soup:
            return []
        return self._extract_episodes(soup)

    def get_episode_servers(self, episode_url: str) -> Dict[str, Any]:
        """
        Get iframe servers from episode page.
        هنا المهمة الرئيسية - نسحب iframes المشاهدة
        """
        soup = self.get_page(episode_url)

        if not soup:
            return {'watch': [], 'download': []}

        result = {
            'watch': [],
            'download': []
        }

        # Method 1: Direct iframes
        iframes = soup.select('iframe[src], iframe[data-src], iframe[data-lazy-src]')

        for iframe in iframes:
            src = (
                iframe.get('src') or
                iframe.get('data-src') or
                iframe.get('data-lazy-src', '')
            )

            # Filter out ads
            if not src or any(x in src.lower() for x in ['ad', 'google', 'facebook', 'twitter']):
                continue

            # Check if it's a video player
            if any(x in src.lower() for x in ['player', 'embed', 'watch', 'video', 'vip', 'play', 'stream']):
                result['watch'].append({
                    'name': 'قصة عشق',
                    'type': 'iframe',
                    'url': src,
                    'quality': '1080p'
                })

        # Method 2: Check for hidden players in data attributes
        elements = soup.select('[data-src], [data-url], [data-video], [data-player]')
        for elem in elements:
            for attr in ['data-src', 'data-url', 'data-video', 'data-player']:
                url = elem.get(attr, '')
                if url and any(x in url.lower() for x in ['player', 'embed', 'video']):
                    result['watch'].append({
                        'name': 'قصة عشق',
                        'type': 'iframe',
                        'url': url,
                        'quality': '1080p'
                    })

        # Method 3: Search in scripts for player URLs
        scripts = soup.select('script:not([src])')
        patterns = [
            r'["\']?(https?://[^"\']*(?:player|embed|video|stream)[^"\']*)["\']?',
            r'iframe["\']?\s*[:=]\s*["\']([^"\']+)["\']',
            r'src\s*[:=]\s*["\']([^"\']*(?:player|embed)[^"\']*)["\']',
            r'file\s*[:=]\s*["\']([^"\']+\.(?:mp4|m3u8))["\']',
        ]

        for script in scripts:
            text = script.get_text()
            for pattern in patterns:
                matches = re.findall(pattern, text, re.I)
                for match in matches:
                    if match.startswith('http') and 'ad' not in match.lower():
                        server_type = 'direct' if match.endswith(('.mp4', '.m3u8')) else 'iframe'
                        result['watch'].append({
                            'name': 'قصة عشق',
                            'type': server_type,
                            'url': match,
                            'quality': '1080p'
                        })

        # Method 4: Look for server buttons/tabs
        server_buttons = soup.select('[data-server], [data-embed], .server-item, .quality-item')
        for btn in server_buttons:
            server_url = btn.get('data-server') or btn.get('data-embed') or btn.get('data-url', '')
            if server_url:
                result['watch'].append({
                    'name': btn.get_text(strip=True) or 'قصة عشق',
                    'type': 'iframe',
                    'url': server_url,
                    'quality': '1080p'
                })

        # Remove duplicates
        seen_urls = set()
        unique_watch = []
        for server in result['watch']:
            if server['url'] not in seen_urls:
                seen_urls.add(server['url'])
                unique_watch.append(server)
        result['watch'] = unique_watch

        return result

    def search_series(self, query: str) -> List[Dict[str, Any]]:
        """Search for a series by name"""
        search_url = f"{self.base_url}/?s={query.replace(' ', '+')}"
        soup = self.get_page(search_url)

        if not soup:
            return []

        results = []

        # Find search results
        result_items = soup.select('a[href*="/watch/tvshows/serie-"]')

        seen = set()
        for item in result_items:
            href = item.get('href', '')
            if href not in seen:
                seen.add(href)
                title = item.get_text(strip=True)
                img = item.select_one('img')
                poster = img.get('src', '') if img else ''

                results.append({
                    'title': title,
                    'url': href,
                    'poster': poster
                })

        return results[:10]

    def find_series_by_name(self, arabic_name: str, turkish_name: str = "") -> Optional[str]:
        """
        البحث عن مسلسل بالاسم العربي أو التركي
        مفيد لمطابقة المسلسلات من أكوام مع قصة عشق
        """
        # Try Arabic name first
        results = self.search_series(arabic_name)
        if results:
            return results[0]['url']

        # Try Turkish name
        if turkish_name:
            results = self.search_series(turkish_name)
            if results:
                return results[0]['url']

        return None


# Test function
if __name__ == "__main__":
    scraper = QissahScraper()

    print("=" * 50)
    print("Testing: Qissah Eshq Scraper")
    print("=" * 50)

    # Test getting series list
    print("\n1. Getting series list...")
    series = scraper.get_series_list()
    print(f"   Found {len(series)} series")
    for s in series[:3]:
        print(f"   - {s['title']}: {s['url']}")

    # Test search
    print("\n2. Searching for 'المحتالون'...")
    results = scraper.search_series("المحتالون")
    print(f"   Found {len(results)} results")
    for r in results[:3]:
        print(f"   - {r['title']}: {r['url']}")
