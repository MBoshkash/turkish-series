"""Akwam Scraper - سكرابر موقع أكوام"""

from typing import Dict, List, Optional, Any
from bs4 import BeautifulSoup
import re
import json
from urllib.parse import unquote
from .base import BaseScraper


class AkwamScraper(BaseScraper):
    """Scraper for ak.sv (Akwam)"""

    def __init__(self):
        super().__init__()
        self.base_url = "https://ak.sv"
        self.source_name = "Akwam"

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

        # === TITLE ===
        # Try h1 first
        h1 = soup.select_one('h1')
        if h1:
            info['title'] = h1.get_text(strip=True).split('|')[0].strip()

        # If still empty, try from URL
        if not info['title']:
            url_match = re.search(r'/series/\d+/([^/]+)', url)
            if url_match:
                info['title'] = unquote(url_match.group(1)).replace('-', ' ')

        # === POSTER IMAGE ===
        # Look for the actual poster image (not placeholder)
        img_selectors = [
            'img[src*="img.downet.net/uploads"]',
            'img[src*="downet.net"][src$=".webp"]',
            'img[src*="downet.net"][src$=".jpg"]',
            '.entry-image img',
            '.poster img',
            'img.img-fluid'
        ]
        for selector in img_selectors:
            img = soup.select_one(selector)
            if img:
                src = img.get('src', '') or img.get('data-src', '')
                if src and 'placeholder' not in src.lower():
                    info['poster'] = src
                    break

        # === DESCRIPTION ===
        desc_elem = soup.select_one('.widget-body p, .entry-content p, [class*="desc"] p')
        if desc_elem:
            info['description'] = desc_elem.get_text(strip=True)

        # === RATING ===
        rating_elem = soup.select_one('[class*="rating"], .imdb')
        if rating_elem:
            rating_text = rating_elem.get_text()
            rating_match = re.search(r'(\d+\.?\d*)', rating_text)
            if rating_match:
                info['rating'] = float(rating_match.group(1))

        # === YEAR ===
        year_match = re.search(r'(20[0-2]\d)', str(soup))
        if year_match:
            info['year'] = year_match.group(1)

        # === GENRES ===
        genre_links = soup.select('a[href*="genre"], a[href*="category"]')
        info['genres'] = list(set([g.get_text(strip=True) for g in genre_links[:5] if g.get_text(strip=True)]))

        # === EPISODES ===
        info['episodes'] = self._extract_episodes(soup)
        info['total_episodes'] = len(info['episodes'])

        return info

    def _extract_episodes(self, soup: BeautifulSoup) -> List[Dict[str, Any]]:
        """Extract episodes list from series page"""
        episodes = []
        seen_episodes = set()

        # Find all episode links
        episode_links = soup.select('a[href*="/episode/"]')

        for link in episode_links:
            href = link.get('href', '')

            # Extract episode ID and number from URL
            # Format: /episode/89932/المحتالون-مترجم/الحلقة-1
            id_match = re.search(r'/episode/(\d+)/', href)

            # Try to get episode number from URL (الحلقة-1 or episode-1)
            ep_num_match = re.search(r'[/-](\d+)/?$', href)
            if not ep_num_match:
                # Try Arabic pattern
                ep_num_match = re.search(r'الحلقة-?(\d+)', unquote(href))

            if id_match and ep_num_match:
                episode_id = id_match.group(1)
                ep_num = int(ep_num_match.group(1))

                if ep_num not in seen_episodes and ep_num < 1000:  # Sanity check
                    seen_episodes.add(ep_num)

                    # Get date if available
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

        # Sort by episode number
        episodes.sort(key=lambda x: x['number'])
        return episodes

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

        # === WATCH LINKS ===
        # نحفظ رابط صفحة المشاهدة (مش المباشر) - لأن الروابط المباشرة مؤقتة
        watch_links = soup.select('a[href*="/watch/"]')
        seen_watch = set()
        for link in watch_links:
            href = link.get('href', '')
            if href and '/watch/' in href and href not in seen_watch:
                seen_watch.add(href)
                full_url = href if href.startswith('http') else f"{self.base_url}{href}"
                result['watch'].append({
                    'name': 'أكوام',
                    'type': 'akwam_page',  # صفحة أكوام - التطبيق هيحلها
                    'url': full_url,
                    'quality': '720p'
                })
                break  # نكتفي برابط واحد

        # === DOWNLOAD LINKS ===
        # نحفظ رابط صفحة التحميل
        download_links = soup.select('a[href*="/download/"]')
        seen_download = set()
        for link in download_links:
            href = link.get('href', '')
            if href and '/download/' in href and href not in seen_download:
                seen_download.add(href)
                full_url = href if href.startswith('http') else f"{self.base_url}{href}"

                # Get size from link text
                size_text = link.get_text()
                size_match = re.search(r'(\d+\.?\d*\s*[MGT]B)', size_text, re.I)

                result['download'].append({
                    'name': 'أكوام',
                    'type': 'akwam_page',
                    'url': full_url,
                    'quality': '720p',
                    'size': size_match.group(1) if size_match else ''
                })
                break  # نكتفي برابط واحد

        return result

    def resolve_download_link(self, redirect_url: str) -> Optional[str]:
        """
        Resolve akwam redirect link to get direct download URL.
        go.ak.sv/link/xxx -> ak.sv/download/xxx -> downet.net/download/xxx.mp4
        """
        try:
            # Step 1: Get the go.ak.sv page
            soup = self.get_page(redirect_url)
            if not soup:
                return None

            # Step 2: Find the next redirect link
            download_link = soup.select_one('a[href*="/download/"]')
            if not download_link:
                return None

            next_url = download_link.get('href', '')
            if not next_url.startswith('http'):
                next_url = f"{self.base_url}{next_url}"

            # Step 3: Get the final download page
            soup2 = self.get_page(next_url)
            if not soup2:
                return None

            # Step 4: Find the direct download link
            direct_link = soup2.select_one('a[href*="downet.net/download"]')
            if direct_link:
                return direct_link.get('href', '')

            # Alternative: search in page text
            page_text = str(soup2)
            match = re.search(r'https?://[^"\'<>\s]+downet\.net/download/[^"\'<>\s]+', page_text)
            if match:
                return match.group(0)

            return None
        except Exception as e:
            print(f"[Akwam] Error resolving download link: {e}")
            return None

    def get_episodes_list(self, url: str) -> List[Dict[str, Any]]:
        """Get list of episodes from series page"""
        soup = self.get_page(url)
        if not soup:
            return []
        return self._extract_episodes(soup)

    def resolve_watch_link(self, redirect_url: str) -> Optional[str]:
        """Resolve akwam watch redirect link to direct video URL"""
        try:
            soup = self.get_page(redirect_url)
            if not soup:
                return None

            # Find watch redirect
            watch_link = soup.select_one('a[href*="/watch/"]')
            if not watch_link:
                return None

            next_url = watch_link.get('href', '')
            if not next_url.startswith('http'):
                next_url = f"{self.base_url}{next_url}"

            soup2 = self.get_page(next_url)
            if not soup2:
                return None

            # Look for video source
            video = soup2.select_one('video source[src], video[src]')
            if video:
                return video.get('src', '')

            # Look for direct MP4/M3U8 in page
            page_text = str(soup2)
            match = re.search(r'https?://[^"\'<>\s]+\.(mp4|m3u8)[^"\'<>\s]*', page_text)
            if match:
                return match.group(0)

            return None
        except Exception as e:
            print(f"[Akwam] Error resolving watch link: {e}")
            return None
