#!/usr/bin/env python3
"""
Turkish Series Scraper - Main Runner
يسحب البيانات من المصادر المختلفة ويحفظها في JSON files
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any, Optional

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent))

from sources.akwam import AkwamScraper
from sources.qissah import QissahScraper


class SeriesScraper:
    """Main scraper that coordinates all sources"""

    def __init__(self, config_path: str = None):
        self.base_dir = Path(__file__).parent.parent
        self.data_dir = self.base_dir / "data"
        self.config_path = config_path or self.data_dir / "config.json"

        # Create directories if needed
        (self.data_dir / "series").mkdir(parents=True, exist_ok=True)
        (self.data_dir / "episodes").mkdir(parents=True, exist_ok=True)

        # Load config
        self.config = self._load_config()

        # Initialize scrapers
        self.scrapers = {
            'akwam': AkwamScraper(),
            'qissah': QissahScraper()
        }

    def _load_config(self) -> Dict:
        """Load configuration file"""
        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except FileNotFoundError:
            print(f"[ERROR] Config file not found: {self.config_path}")
            return {"series": [], "sources": {}, "settings": {}}

    def _save_json(self, path: Path, data: Dict):
        """Save data to JSON file"""
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"[SAVED] {path}")

    def scrape_series(self, series_config: Dict) -> Optional[Dict]:
        """
        Scrape a single series from all enabled sources
        """
        series_id = series_config['id']
        series_name = series_config['name']

        print(f"\n{'='*50}")
        print(f"Scraping: {series_name} (ID: {series_id})")
        print('='*50)

        # Initialize series data
        series_data = {
            'id': series_id,
            'title': series_name,
            'original_title': '',
            'description': '',
            'poster': '',
            'backdrop': '',
            'year': '',
            'country': 'تركيا',
            'rating': 0.0,
            'genres': [],
            'quality': '',
            'cast': [],
            'total_episodes': 0,
            'status': 'ongoing',
            'last_updated': datetime.utcnow().isoformat() + 'Z',
            'episodes': []
        }

        episodes_data = {}  # ep_number -> episode_data

        # ============================================
        # 1. Scrape from Akwam (primary source)
        # ============================================
        akwam_config = series_config.get('sources', {}).get('akwam', {})
        if akwam_config.get('url'):
            print(f"\n[Akwam] Scraping from: {akwam_config['url']}")

            akwam = self.scrapers['akwam']
            info = akwam.get_series_info(akwam_config['url'])

            if info:
                # Update series data from Akwam
                series_data.update({
                    'title': info.get('title') or series_data['title'],
                    'original_title': info.get('original_title', ''),
                    'description': info.get('description', ''),
                    'poster': info.get('poster', ''),
                    'year': info.get('year', ''),
                    'rating': info.get('rating', 0.0),
                    'genres': info.get('genres', []),
                    'quality': info.get('quality', ''),
                    'cast': info.get('cast', []),
                    'total_episodes': info.get('total_episodes', 0),
                })

                # Process episodes from Akwam
                for ep in info.get('episodes', []):
                    ep_num = ep['number']

                    if ep_num not in episodes_data:
                        episodes_data[ep_num] = {
                            'series_id': series_id,
                            'series_title': series_data['title'],
                            'episode_number': ep_num,
                            'title': f'الحلقة {ep_num}',
                            'date_added': ep.get('date_added', ''),
                            'last_updated': datetime.utcnow().isoformat() + 'Z',
                            'servers': {
                                'watch': [],
                                'download': []
                            }
                        }

                    # Get servers for this episode
                    if ep.get('url'):
                        servers = akwam.get_episode_servers(ep['url'])

                        # Add Akwam watch servers
                        for server in servers.get('watch', []):
                            episodes_data[ep_num]['servers']['watch'].append({
                                'name': 'أكوام',
                                'type': server.get('type', 'redirect'),
                                'url': server['url'],
                                'quality': server.get('quality', '720p'),
                                'source': 'akwam'
                            })

                        # Add Akwam download servers
                        for server in servers.get('download', []):
                            episodes_data[ep_num]['servers']['download'].append({
                                'name': 'أكوام',
                                'url': server['url'],
                                'quality': server.get('quality', '720p'),
                                'size': server.get('size', ''),
                                'source': 'akwam'
                            })

                print(f"[Akwam] ✓ Got {len(info.get('episodes', []))} episodes")
            else:
                print("[Akwam] ✗ Failed to get series info")

        # ============================================
        # 2. Add Qissah Eshq iframes (manual links)
        # ============================================
        qissah_config = series_config.get('sources', {}).get('qissah', {})
        qissah_episodes = qissah_config.get('episodes', {})

        if qissah_episodes:
            print(f"\n[Qissah] Adding {len(qissah_episodes)} manual episode links")

            for ep_str, ep_url in qissah_episodes.items():
                ep_num = int(ep_str)

                if ep_num not in episodes_data:
                    episodes_data[ep_num] = {
                        'series_id': series_id,
                        'series_title': series_data['title'],
                        'episode_number': ep_num,
                        'title': f'الحلقة {ep_num}',
                        'date_added': '',
                        'last_updated': datetime.utcnow().isoformat() + 'Z',
                        'servers': {
                            'watch': [],
                            'download': []
                        }
                    }

                # Add Qissah iframe server
                # الموقع محمي، فهنضيف الرابط كـ webview بدل iframe
                episodes_data[ep_num]['servers']['watch'].append({
                    'name': 'قصة عشق',
                    'type': 'webview',  # سيتم فتحه في WebView في التطبيق
                    'url': ep_url,
                    'quality': '1080p',
                    'source': 'qissah'
                })

            print(f"[Qissah] ✓ Added {len(qissah_episodes)} episode links")

        # ============================================
        # 3. Build final episodes list
        # ============================================
        series_data['episodes'] = []
        for ep_num in sorted(episodes_data.keys()):
            ep = episodes_data[ep_num]
            series_data['episodes'].append({
                'number': ep_num,
                'title': ep['title'],
                'date_added': ep.get('date_added', ''),
                'servers_count': len(ep['servers']['watch']) + len(ep['servers']['download'])
            })

        series_data['total_episodes'] = len(series_data['episodes'])

        # ============================================
        # 4. Save files
        # ============================================
        # Save series info
        series_path = self.data_dir / "series" / f"{series_id}.json"
        self._save_json(series_path, series_data)

        # Save each episode
        for ep_num, ep_data in episodes_data.items():
            ep_path = self.data_dir / "episodes" / f"{series_id}_{ep_num:02d}.json"
            self._save_json(ep_path, ep_data)

        return series_data

    def scrape_all(self) -> List[Dict]:
        """Scrape all enabled series"""
        print("\n" + "="*60)
        print("Turkish Series Scraper - Starting")
        print(f"Time: {datetime.utcnow().isoformat()}")
        print("="*60)

        all_series = []

        for series_config in self.config.get('series', []):
            if not series_config.get('enabled', True):
                print(f"\n[SKIP] {series_config['name']} (disabled)")
                continue

            try:
                series_data = self.scrape_series(series_config)
                if series_data:
                    all_series.append({
                        'id': series_data['id'],
                        'title': series_data['title'],
                        'original_title': series_data.get('original_title', ''),
                        'poster': series_data.get('poster', ''),
                        'year': series_data.get('year', ''),
                        'rating': series_data.get('rating', 0),
                        'genres': series_data.get('genres', []),
                        'episodes_count': series_data.get('total_episodes', 0),
                        'last_episode': series_data['episodes'][-1]['number'] if series_data['episodes'] else 0,
                        'last_updated': series_data['last_updated'],
                        'status': series_data.get('status', 'ongoing')
                    })
            except Exception as e:
                print(f"[ERROR] Failed to scrape {series_config['name']}: {e}")
                import traceback
                traceback.print_exc()

        # Save main series list
        series_list = {
            'last_updated': datetime.utcnow().isoformat() + 'Z',
            'total': len(all_series),
            'series': all_series
        }
        self._save_json(self.data_dir / "series.json", series_list)

        print("\n" + "="*60)
        print(f"Scraping Complete! Processed {len(all_series)} series")
        print("="*60)

        return all_series

    def scrape_single(self, series_id: str) -> Optional[Dict]:
        """Scrape a single series by ID"""
        for series_config in self.config.get('series', []):
            if series_config['id'] == series_id:
                return self.scrape_series(series_config)

        print(f"[ERROR] Series not found: {series_id}")
        return None


def main():
    """Main entry point"""
    import argparse

    parser = argparse.ArgumentParser(description='Turkish Series Scraper')
    parser.add_argument('--series', '-s', help='Scrape single series by ID')
    parser.add_argument('--all', '-a', action='store_true', help='Scrape all series')
    parser.add_argument('--config', '-c', help='Path to config file')

    args = parser.parse_args()

    scraper = SeriesScraper(config_path=args.config)

    if args.series:
        scraper.scrape_single(args.series)
    else:
        scraper.scrape_all()


if __name__ == "__main__":
    main()
