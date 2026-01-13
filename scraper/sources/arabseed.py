"""ArabSeed Scraper - سكرابر موقع عرب سيد للمسلسلات التركية"""

from typing import Dict, List, Optional, Any
from bs4 import BeautifulSoup
import re
import time
import base64
from .base import BaseScraper


class ArabSeedScraper(BaseScraper):
    """Scraper for ArabSeed - Turkish Series"""

    def __init__(self):
        super().__init__()
        self.base_url = "https://a.asd.homes"
        self.source_name = "ArabSeed"
        self.delay_between_requests = 1.5

        # TDM deep link format for non-direct downloads
        self.tdm_open_format = "tdm://open?url={}"

    def _decode_base64_url(self, encoded: str) -> str:
        """Decode base64 URL (handles both standard and URL-safe)"""
        try:
            padding = 4 - len(encoded) % 4
            if padding != 4:
                encoded += '=' * padding

            try:
                return base64.urlsafe_b64decode(encoded).decode('utf-8')
            except:
                return base64.b64decode(encoded).decode('utf-8')
        except:
            return ""

    def _is_direct_download(self, url: str) -> bool:
        """Check if URL is a direct download link (not requiring browser)"""
        direct_hosts = [
            'filespayouts.com',
            'up-4ever.net',
            'up4ever.net',
            'mediafire.com',
            'mega.nz',
        ]
        return any(host in url.lower() for host in direct_hosts)

    def _format_download_url(self, url: str) -> str:
        """Format download URL - wrap non-direct links with TDM"""
        if self._is_direct_download(url):
            return url
        return self.tdm_open_format.format(url)

    def get_series_info(self, url: str) -> Optional[Dict[str, Any]]:
        """
        Get series information from an episode page
        (ArabSeed shows series info on every episode page)
        """
        soup = self.get_page(url)
        if not soup:
            return None

        info = {
            'title': '',
            'original_title': '',
            'description': '',
            'poster': '',
            'year': '',
            'country': 'تركيا',
            'language': '',
            'rating': 0.0,
            'genres': [],
            'tags': [],
            'quality': '',
            'duration': '',
            'cast': [],
            'total_episodes': 0,
            'episodes': []
        }

        # Title
        title_elem = soup.select_one('h1')
        if title_elem:
            info['title'] = title_elem.get_text(strip=True)

        # Episodes list
        episodes = self._extract_episodes(soup)
        info['episodes'] = episodes
        info['total_episodes'] = len(episodes)

        return info

    def get_episodes_list(self, url: str) -> List[Dict[str, Any]]:
        """Get list of all episodes from an episode page"""
        soup = self.get_page(url)
        if not soup:
            return []

        return self._extract_episodes(soup)

    def _extract_episodes(self, soup: BeautifulSoup) -> List[Dict[str, Any]]:
        """Extract episodes list from page"""
        episodes = []

        episode_items = soup.select('ul.episodes__list li a')

        for item in episode_items:
            href = item.get('href', '')
            ep_num_elem = item.select_one('.epi__num b')

            if ep_num_elem and href:
                try:
                    ep_num = int(ep_num_elem.get_text(strip=True))
                except ValueError:
                    continue

                episodes.append({
                    'number': ep_num,
                    'url': href,
                    'watch_url': href.rstrip('/') + '/watch/',
                    'download_url': href.rstrip('/') + '/download/',
                    'title': f'الحلقة {ep_num}'
                })

        episodes.sort(key=lambda x: x['number'])
        return episodes

    def get_seasons_list(self, url: str) -> List[Dict[str, Any]]:
        """Get list of seasons from episode page"""
        soup = self.get_page(url)
        if not soup:
            return []

        seasons = []
        season_items = soup.select('#seasons__list ul li[data-term]')

        for item in season_items:
            term_id = item.get('data-term')
            name_elem = item.select_one('span')
            is_selected = 'selected' in item.get('class', [])

            if term_id and name_elem:
                seasons.append({
                    'term_id': term_id,
                    'name': name_elem.get_text(strip=True),
                    'selected': is_selected
                })

        return seasons

    def get_episode_servers(self, url: str) -> Dict[str, Any]:
        """
        Get watch iframes and download links for an episode
        Returns first 2 servers per quality for both watch and download
        """
        result = {
            'watch': [],
            'download': []
        }

        # Get watch servers
        watch_url = url.rstrip('/') + '/watch/' if not url.endswith('/watch/') else url
        watch_servers = self._get_watch_servers(watch_url)
        result['watch'] = watch_servers

        # Get download servers
        download_url = url.rstrip('/').replace('/watch/', '') + '/download/'
        download_servers = self._get_download_servers(download_url)
        result['download'] = download_servers

        return result

    def _get_watch_servers(self, watch_url: str) -> List[Dict[str, Any]]:
        """
        Get watch iframe URLs for each quality (first 2 per quality)
        """
        print(f"[ArabSeed] Getting watch servers from: {watch_url}")

        soup = self.get_page(watch_url)
        if not soup:
            return []

        servers = []

        # Get qualities available
        qualities = []
        quality_items = soup.select('li[data-quality]')
        for item in quality_items:
            q = item.get('data-quality')
            if q:
                qualities.append(q)

        if not qualities:
            qualities = ['720']  # Default

        # Get first iframe (usually default quality)
        iframe = soup.select_one('iframe[src]')
        if iframe:
            src = iframe.get('src', '')

            # Decode if base64
            decoded_url = src
            if 'url=' in src:
                match = re.search(r'url=([A-Za-z0-9+/=_-]+)', src)
                if match:
                    decoded_url = self._decode_base64_url(match.group(1))
            elif src.startswith('/play.php'):
                decoded_url = self.base_url + src

            if decoded_url:
                # Get direct video URL from embed page
                direct_url = self._get_direct_video_url(decoded_url)

                servers.append({
                    'name': 'سيرفر عرب سيد',
                    'type': 'iframe',
                    'url': decoded_url,
                    'direct_url': direct_url,
                    'quality': qualities[0] + 'p' if qualities else '720p',
                    'source': 'arabseed'
                })

        # Get server buttons for additional servers
        server_items = soup.select('li[data-server]')
        server_count = 0
        for item in server_items[1:]:  # Skip first (already got default)
            if server_count >= 1:  # Only get 1 more (total 2)
                break

            server_name = item.get_text(strip=True)
            servers.append({
                'name': server_name or f'سيرفر {len(servers) + 1}',
                'type': 'iframe',
                'url': watch_url,  # Server switching is done via JS
                'quality': qualities[0] + 'p' if qualities else '720p',
                'source': 'arabseed'
            })
            server_count += 1

        print(f"[ArabSeed] Found {len(servers)} watch servers")
        return servers

    def _get_direct_video_url(self, embed_url: str) -> Optional[str]:
        """Extract direct video URL from embed page (reviewrate.net)"""
        if not embed_url or 'reviewrate.net' not in embed_url:
            return None

        try:
            response = self.scraper.get(embed_url, headers=self.headers, timeout=30)
            soup = BeautifulSoup(response.text, 'lxml')

            # Look for video source
            source = soup.select_one('video source[src]')
            if source:
                return source.get('src')

            # Look in scripts
            for script in soup.select('script'):
                text = script.string or ''

                # MP4 URLs
                mp4_match = re.search(r'https?://[^\s\'"<>]+\.mp4[^\s\'"<>]*', text)
                if mp4_match:
                    return mp4_match.group(0)

                # M3U8 URLs
                m3u8_match = re.search(r'https?://[^\s\'"<>]+\.m3u8[^\s\'"<>]*', text)
                if m3u8_match:
                    return m3u8_match.group(0)

        except Exception as e:
            print(f"[ArabSeed] Error getting direct URL: {e}")

        return None

    def _get_download_servers(self, download_url: str) -> List[Dict[str, Any]]:
        """
        Get download links for each quality (first 2 per quality)
        """
        print(f"[ArabSeed] Getting download servers from: {download_url}")

        soup = self.get_page(download_url)
        if not soup:
            return []

        servers = []

        # Find all download sections by quality
        quality_sections = soup.select('div[data-quality]')

        for section in quality_sections:
            quality = section.get('data-quality', 'unknown')

            # Find download links in this section (first 2 only)
            links = section.select('a[href*="/l/"]')[:2]

            for link in links:
                href = link.get('href', '')
                name = link.get_text(strip=True)

                # Decode the link
                decoded = ""
                match = re.search(r'/l/([A-Za-z0-9+/=_-]+)', href)
                if match:
                    decoded = self._decode_base64_url(match.group(1))

                if decoded:
                    # Check if direct or needs TDM
                    is_direct = self._is_direct_download(decoded)
                    final_url = decoded if is_direct else self._format_download_url(decoded)

                    servers.append({
                        'name': self._clean_server_name(name),
                        'url': final_url,
                        'original_url': decoded,
                        'quality': f'{quality}p',
                        'is_direct': is_direct,
                        'source': 'arabseed'
                    })

        print(f"[ArabSeed] Found {len(servers)} download servers")
        return servers

    def _clean_server_name(self, name: str) -> str:
        """Clean up server name"""
        # Remove common suffixes
        name = re.sub(r'التحميل الان.*', '', name)
        name = re.sub(r'\s*-\s*\d+p.*', '', name)
        name = name.strip()

        # Fallback
        if not name:
            return 'سيرفر عرب سيد'

        return name

    def scrape_episode_full(self, episode_url: str) -> Dict[str, Any]:
        """
        Full scrape of an episode - returns all data
        """
        print(f"\n[ArabSeed] Scraping episode: {episode_url}")

        servers = self.get_episode_servers(episode_url)

        return {
            'url': episode_url,
            'servers': servers
        }
