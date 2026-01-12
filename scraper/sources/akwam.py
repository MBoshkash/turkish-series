"""Akwam Scraper - سكرابر موقع أكوام"""

from typing import Dict, List, Optional, Any
from bs4 import BeautifulSoup
import re
import json
from .base import BaseScraper


class AkwamScraper(BaseScraper):
    """Scraper for ak.sv (Akwam)"""

    def __init__(self):
        super().__init__()
        self.base_url = "https://ak.sv"
        self.source_name = "Akwam"

    def get_turkish_series_list(self, page: int = 1) -> List[Dict[str, Any]]:
        """Get list of Turkish series from section 32"""
        url = f"{self.base_url}/series?section=32&page={page}"
        soup = self.get_page(url)

        if not soup:
            return []

        series_list = []

        # Find series cards
        cards = soup.select('.entry-box, .entry-image, [class*="movie"], [class*="series"]')

        # Try to find links to series
        links = soup.select('a[href*="/series/"]')

        seen_ids = set()
        for link in links:
            href = link.get('href', '')
            match = re.search(r'/series/(\d+)/([^"\']+)', href)
            if match:
                series_id = match.group(1)
                if series_id not in seen_ids:
                    seen_ids.add(series_id)
                    slug = match.group(2)

                    # Try to get title and image
                    title = link.get_text(strip=True) or slug.replace('-', ' ')
                    img = link.select_one('img')
                    poster = img.get('src', '') if img else ''

                    series_list.append({
                        'id': series_id,
                        'slug': slug,
                        'title': title,
                        'poster': poster,
                        'url': f"{self.base_url}/series/{series_id}/{slug}"
                    })

        return series_list

    def get_series_info(self, url: str) -> Optional[Dict[str, Any]]:
        """Get detailed series information"""
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
            'country': 'تركيا',
            'rating': 0.0,
            'genres': [],
            'quality': '',
            'age_rating': '',
            'duration': '',
            'cast': [],
            'total_episodes': 0,
            'status': 'ongoing',
            'episodes': []
        }

        # Title - try multiple selectors
        title_selectors = [
            'h1.entry-title', 'h1', '.entry-title',
            '[class*="title"]', 'title'
        ]
        for selector in title_selectors:
            title = self.get_text(soup, selector)
            if title and len(title) > 2:
                info['title'] = title.split('|')[0].strip()
                break

        # Original title
        original_title = soup.select_one('[class*="original"], .original-title')
        if original_title:
            info['original_title'] = original_title.get_text(strip=True)

        # Description/Story
        desc_selectors = [
            '.entry-content p', '.story', '.description',
            '[class*="desc"]', '.widget-body p'
        ]
        for selector in desc_selectors:
            desc = self.get_text(soup, selector)
            if desc and len(desc) > 20:
                info['description'] = desc
                break

        # Poster image
        poster_selectors = [
            '.entry-image img', '.poster img',
            '[class*="poster"] img', 'img[class*="thumb"]'
        ]
        for selector in poster_selectors:
            poster = self.get_attr(soup, selector, 'src')
            if poster:
                info['poster'] = poster if poster.startswith('http') else f"{self.base_url}{poster}"
                break

        # Rating
        rating_text = soup.select_one('[class*="rating"], .imdb, [class*="score"]')
        if rating_text:
            rating_match = re.search(r'(\d+\.?\d*)', rating_text.get_text())
            if rating_match:
                info['rating'] = float(rating_match.group(1))

        # Year
        year_match = re.search(r'(20[0-2]\d)', soup.get_text())
        if year_match:
            info['year'] = year_match.group(1)

        # Quality
        quality = soup.select_one('[class*="quality"], .badge')
        if quality:
            info['quality'] = quality.get_text(strip=True)

        # Genres
        genre_links = soup.select('a[href*="genre"], a[href*="category"], .genres a')
        info['genres'] = [g.get_text(strip=True) for g in genre_links[:5]]

        # Cast
        cast_elements = soup.select('[class*="cast"] a, .actors a, [class*="star"] a')
        for cast in cast_elements[:10]:
            name = cast.get_text(strip=True)
            if name:
                info['cast'].append({'name': name, 'role': ''})

        # Episodes list
        info['episodes'] = self._extract_episodes(soup, series_id)
        info['total_episodes'] = len(info['episodes'])

        return info

    def _extract_episodes(self, soup: BeautifulSoup, series_id: str) -> List[Dict[str, Any]]:
        """Extract episodes list from series page"""
        episodes = []

        # Find episode links
        episode_links = soup.select('a[href*="/episode/"], a[href*="episode"]')

        seen_episodes = set()
        for link in episode_links:
            href = link.get('href', '')

            # Try to extract episode number
            ep_match = re.search(r'/(\d+)/?$', href) or re.search(r'episode[/-]?(\d+)', href, re.I)
            if ep_match:
                ep_num = int(ep_match.group(1))
                if ep_num not in seen_episodes:
                    seen_episodes.add(ep_num)

                    # Get date if available
                    date_elem = link.find_parent().select_one('[class*="date"], time, .meta')
                    date_added = date_elem.get_text(strip=True) if date_elem else ''

                    episodes.append({
                        'number': ep_num,
                        'title': f'الحلقة {ep_num}',
                        'url': href if href.startswith('http') else f"{self.base_url}{href}",
                        'date_added': date_added
                    })

        # Sort by episode number
        episodes.sort(key=lambda x: x['number'])
        return episodes

    def get_episodes_list(self, url: str) -> List[Dict[str, Any]]:
        """Get list of episodes from series page"""
        soup = self.get_page(url)
        if not soup:
            return []

        match = re.search(r'/series/(\d+)/', url)
        series_id = match.group(1) if match else ""

        return self._extract_episodes(soup, series_id)

    def get_episode_servers(self, episode_url: str) -> Dict[str, Any]:
        """Get watch and download servers for an episode"""
        soup = self.get_page(episode_url)

        if not soup:
            return {'watch': [], 'download': []}

        result = {
            'watch': [],
            'download': [],
            'screenshots': [],
            'info': {}
        }

        # Extract episode info
        result['info'] = {
            'duration': self.get_text(soup, '[class*="duration"], .runtime'),
            'quality': self.get_text(soup, '[class*="quality"]'),
            'size': ''
        }

        # Find watch links (go.ak.sv/watch/...)
        watch_links = soup.select('a[href*="go.ak.sv/watch"], a[href*="/watch/"]')
        for link in watch_links:
            href = link.get('href', '')
            if href:
                result['watch'].append({
                    'name': 'أكوام',
                    'type': 'redirect',
                    'url': href,
                    'quality': self.get_text(soup, '[class*="quality"]') or '720p'
                })

        # Find download links (go.ak.sv/link/...)
        download_links = soup.select('a[href*="go.ak.sv/link"], a[href*="/link/"], a[href*="/download/"]')
        for link in download_links:
            href = link.get('href', '')
            size_text = link.get_text()
            size_match = re.search(r'(\d+\.?\d*\s*[MGT]B)', size_text, re.I)

            if href:
                result['download'].append({
                    'name': 'أكوام',
                    'url': href,
                    'quality': '720p',
                    'size': size_match.group(1) if size_match else ''
                })

        # Screenshots
        screenshots = soup.select('[class*="screenshot"] img, .gallery img, [class*="screen"] img')
        result['screenshots'] = [
            img.get('src', '') for img in screenshots
            if img.get('src', '').startswith('http')
        ]

        return result

    def resolve_download_link(self, redirect_url: str) -> Optional[str]:
        """
        Resolve akwam redirect link to get direct download URL.
        redirect_url: https://go.ak.sv/link/14170
        returns: https://s251d3.downet.net/download/.../file.mp4
        """
        # Step 1: Get the go.ak.sv page
        soup = self.get_page(redirect_url)
        if not soup:
            return None

        # Step 2: Find the next redirect link (ak.sv/download/...)
        download_link = soup.select_one('a[href*="ak.sv/download"], a[href*="/download/"]')
        if not download_link:
            return None

        next_url = download_link.get('href', '')
        if not next_url.startswith('http'):
            next_url = f"{self.base_url}{next_url}"

        # Step 3: Get the final download page
        soup2 = self.get_page(next_url)
        if not soup2:
            return None

        # Step 4: Find the direct download link (downet.net)
        direct_link = soup2.select_one('a[href*="downet.net"], a[href*=".mp4"]')
        if direct_link:
            return direct_link.get('href', '')

        # Alternative: look in script tags
        scripts = soup2.select('script')
        for script in scripts:
            script_text = script.get_text()
            match = re.search(r'https?://[^"\']+downet\.net[^"\']+\.mp4', script_text)
            if match:
                return match.group(0)

        return None

    def resolve_watch_link(self, redirect_url: str) -> Optional[str]:
        """
        Resolve akwam watch redirect link.
        Similar process to download link resolution.
        """
        soup = self.get_page(redirect_url)
        if not soup:
            return None

        # Find watch redirect
        watch_link = soup.select_one('a[href*="ak.sv/watch"], a[href*="/watch/"]')
        if not watch_link:
            return None

        next_url = watch_link.get('href', '')
        if not next_url.startswith('http'):
            next_url = f"{self.base_url}{next_url}"

        soup2 = self.get_page(next_url)
        if not soup2:
            return None

        # Look for video source or iframe
        video = soup2.select_one('video source, video')
        if video:
            return video.get('src', '')

        iframe = soup2.select_one('iframe[src*="player"], iframe[src*="embed"]')
        if iframe:
            return iframe.get('src', '')

        # Check scripts for video URL
        scripts = soup2.select('script')
        for script in scripts:
            script_text = script.get_text()
            match = re.search(r'https?://[^"\']+\.(mp4|m3u8)', script_text)
            if match:
                return match.group(0)

        return None


# Test function
if __name__ == "__main__":
    scraper = AkwamScraper()

    # Test getting Turkish series list
    print("=" * 50)
    print("Testing: Get Turkish Series List")
    print("=" * 50)
    series = scraper.get_turkish_series_list(page=1)
    print(f"Found {len(series)} series")
    for s in series[:3]:
        print(f"  - {s['title']} (ID: {s['id']})")

    if series:
        # Test getting series info
        print("\n" + "=" * 50)
        print(f"Testing: Get Series Info for {series[0]['title']}")
        print("=" * 50)
        info = scraper.get_series_info(series[0]['url'])
        if info:
            print(f"  Title: {info['title']}")
            print(f"  Rating: {info['rating']}")
            print(f"  Episodes: {info['total_episodes']}")
            print(f"  Description: {info['description'][:100]}...")
