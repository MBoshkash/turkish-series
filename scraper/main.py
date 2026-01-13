#!/usr/bin/env python3
"""
Turkish Series Scraper - Main Runner
Supports multiple sources (Akwam, ArabSeed) with new-only or full scrape modes
"""

import json
import os
import sys
import io
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Any, Optional, Set

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
sys.path.insert(0, str(Path(__file__).parent))

from sources.akwam import AkwamScraper
from sources.arabseed import ArabSeedScraper


class SeriesScraper:
    def __init__(self, config_path: str = None, new_only: bool = True):
        self.base_dir = Path(__file__).parent.parent
        self.data_dir = self.base_dir / "data"
        self.config_path = config_path or self.data_dir / "config.json"
        self.new_only = new_only

        (self.data_dir / "series").mkdir(parents=True, exist_ok=True)
        (self.data_dir / "episodes").mkdir(parents=True, exist_ok=True)

        self.config = self._load_config()
        self.scrapers = {'akwam': AkwamScraper(), 'arabseed': ArabSeedScraper()}

    def _load_config(self) -> Dict:
        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except FileNotFoundError:
            return {"series": [], "sources": {}, "settings": {}}

    def _save_json(self, path: Path, data: Dict):
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"[SAVED] {path}")

    def _load_json(self, path: Path) -> Optional[Dict]:
        try:
            with open(path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return None

    def _get_existing_episodes(self, series_id: str) -> Set[int]:
        existing = set()
        series_path = self.data_dir / "series" / f"{series_id}.json"
        series_data = self._load_json(series_path)
        if series_data and 'episodes' in series_data:
            for ep in series_data['episodes']:
                existing.add(ep['number'])
        return existing

    def scrape_series(self, series_config: Dict, force_all: bool = False) -> Optional[Dict]:
        series_id = series_config['id']
        series_name = series_config['name']
        mode = 'ALL' if force_all or not self.new_only else 'NEW only'

        print(f"\n{'='*50}\nScraping: {series_name} (ID: {series_id})\nMode: {mode}\n{'='*50}")

        existing_episodes = set()
        if self.new_only and not force_all:
            existing_episodes = self._get_existing_episodes(series_id)
            if existing_episodes:
                print(f"[INFO] Found {len(existing_episodes)} existing episodes")

        series_path = self.data_dir / "series" / f"{series_id}.json"
        series_data = self._load_json(series_path) or {
            'id': series_id, 'title': series_name, 'original_title': '',
            'description': '', 'poster': '', 'backdrop': '', 'year': '',
            'country': '', 'language': '', 'rating': 0.0, 'genres': [],
            'tags': [], 'quality': '', 'duration': '', 'age_rating': '',
            'cast': [], 'total_episodes': 0, 'status': 'ongoing',
            'last_updated': datetime.utcnow().isoformat() + 'Z', 'episodes': []
        }

        episodes_data = {}
        for ep in series_data.get('episodes', []):
            ep_path = self.data_dir / "episodes" / f"{series_id}_{ep['number']:02d}.json"
            ep_data = self._load_json(ep_path)
            if ep_data:
                episodes_data[ep['number']] = ep_data

        # Akwam
        akwam_config = series_config.get('sources', {}).get('akwam', {})
        if akwam_config.get('url'):
            print(f"\n[Akwam] Scraping: {akwam_config['url']}")
            akwam = self.scrapers['akwam']
            info = akwam.get_series_info(akwam_config['url'])
            if info:
                for k in ['title', 'original_title', 'description', 'poster', 'year',
                          'country', 'language', 'rating', 'genres', 'tags', 'quality',
                          'duration', 'cast']:
                    if info.get(k):
                        series_data[k] = info[k]
                new_count = 0
                for ep in info.get('episodes', []):
                    ep_num = ep['number']
                    if self.new_only and not force_all and ep_num in existing_episodes:
                        continue
                    new_count += 1
                    if ep_num not in episodes_data:
                        episodes_data[ep_num] = {
                            'series_id': series_id, 'series_title': series_data['title'],
                            'episode_number': ep_num, 'title': f'الحلقة {ep_num}',
                            'date_added': ep.get('date_added', ''),
                            'last_updated': datetime.utcnow().isoformat() + 'Z',
                            'servers': {'watch': [], 'download': []}
                        }
                    if ep.get('url'):
                        servers = akwam.get_episode_servers(ep['url'])
                        for s in servers.get('watch', []):
                            episodes_data[ep_num]['servers']['watch'].append({
                                'name': 'أكوام', 'type': s.get('type', 'redirect'),
                                'url': s['url'], 'quality': s.get('quality', '720p'), 'source': 'akwam'
                            })
                        for s in servers.get('download', []):
                            episodes_data[ep_num]['servers']['download'].append({
                                'name': 'أكوام', 'url': s['url'], 'quality': s.get('quality', '720p'),
                                'size': s.get('size', ''), 'source': 'akwam'
                            })
                print(f"[Akwam] Got {len(info.get('episodes', []))} total, {new_count} new")

        # ArabSeed
        arabseed_config = series_config.get('sources', {}).get('arabseed', {})
        if arabseed_config.get('url'):
            print(f"\n[ArabSeed] Scraping: {arabseed_config['url']}")
            arabseed = self.scrapers['arabseed']
            episodes_list = arabseed.get_episodes_list(arabseed_config['url'])
            if episodes_list:
                new_count = 0
                for ep in episodes_list:
                    ep_num = ep['number']
                    if self.new_only and not force_all and ep_num in existing_episodes:
                        continue
                    new_count += 1
                    if ep_num not in episodes_data:
                        episodes_data[ep_num] = {
                            'series_id': series_id, 'series_title': series_data['title'],
                            'episode_number': ep_num, 'title': f'الحلقة {ep_num}',
                            'date_added': '', 'last_updated': datetime.utcnow().isoformat() + 'Z',
                            'servers': {'watch': [], 'download': []}
                        }
                    servers = arabseed.get_episode_servers(ep['url'])
                    for s in servers.get('watch', []):
                        episodes_data[ep_num]['servers']['watch'].append({
                            'name': s.get('name', 'عرب سيد'), 'type': s.get('type', 'iframe'),
                            'url': s['url'], 'direct_url': s.get('direct_url', ''),
                            'quality': s.get('quality', '720p'), 'source': 'arabseed'
                        })
                    for s in servers.get('download', []):
                        episodes_data[ep_num]['servers']['download'].append({
                            'name': s.get('name', 'عرب سيد'), 'url': s['url'],
                            'quality': s.get('quality', '720p'),
                            'is_direct': s.get('is_direct', False), 'source': 'arabseed'
                        })
                print(f"[ArabSeed] Got {len(episodes_list)} total, {new_count} new")

        series_data['episodes'] = []
        for ep_num in sorted(episodes_data.keys()):
            ep = episodes_data[ep_num]
            series_data['episodes'].append({
                'number': ep_num, 'title': ep['title'], 'date_added': ep.get('date_added', ''),
                'servers_count': len(ep['servers']['watch']) + len(ep['servers']['download'])
            })

        series_data['total_episodes'] = len(series_data['episodes'])
        series_data['last_updated'] = datetime.utcnow().isoformat() + 'Z'

        self._save_json(series_path, series_data)
        for ep_num, ep_data in episodes_data.items():
            if not self.new_only or force_all or ep_num not in existing_episodes:
                self._save_json(self.data_dir / "episodes" / f"{series_id}_{ep_num:02d}.json", ep_data)

        return series_data

    def scrape_all(self, force_all: bool = False) -> List[Dict]:
        mode = "ALL" if force_all else "NEW only"
        print(f"\n{'='*60}\nTurkish Series Scraper\nMode: {mode}\nTime: {datetime.now(timezone.utc).isoformat()}\n{'='*60}")

        all_series = []
        for cfg in self.config.get('series', []):
            if not cfg.get('enabled', True):
                print(f"\n[SKIP] {cfg['name']} (disabled)")
                continue
            try:
                data = self.scrape_series(cfg, force_all=force_all)
                if data:
                    last_date = data['episodes'][-1].get('date_added', '') if data['episodes'] else ''
                    all_series.append({
                        'id': data['id'], 'title': data['title'],
                        'original_title': data.get('original_title', ''),
                        'poster': data.get('poster', ''), 'year': data.get('year', ''),
                        'country': data.get('country', ''), 'language': data.get('language', ''),
                        'rating': data.get('rating', 0), 'genres': data.get('genres', []),
                        'tags': data.get('tags', []), 'quality': data.get('quality', ''),
                        'duration': data.get('duration', ''),
                        'episodes_count': data.get('total_episodes', 0),
                        'last_episode': data['episodes'][-1]['number'] if data['episodes'] else 0,
                        'last_episode_date': last_date, 'last_updated': data['last_updated'],
                        'status': data.get('status', 'ongoing')
                    })
            except Exception as e:
                print(f"[ERROR] {cfg['name']}: {e}")
                import traceback
                traceback.print_exc()

        self._save_json(self.data_dir / "series.json", {
            'last_updated': datetime.utcnow().isoformat() + 'Z',
            'total': len(all_series), 'series': all_series
        })
        print(f"\n{'='*60}\nComplete! {len(all_series)} series\n{'='*60}")
        return all_series

    def scrape_single(self, series_id: str, force_all: bool = False) -> Optional[Dict]:
        for cfg in self.config.get('series', []):
            if cfg['id'] == series_id:
                return self.scrape_series(cfg, force_all=force_all)
        print(f"[ERROR] Series not found: {series_id}")
        return None


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Turkish Series Scraper')
    parser.add_argument('--series', '-s', help='Scrape single series by ID')
    parser.add_argument('--all', '-a', action='store_true', help='Scrape all series')
    parser.add_argument('--full', '-f', action='store_true', help='Full scrape (all episodes)')
    parser.add_argument('--config', '-c', help='Path to config file')
    args = parser.parse_args()

    scraper = SeriesScraper(config_path=args.config, new_only=not args.full)
    if args.series:
        scraper.scrape_single(args.series, force_all=args.full)
    else:
        scraper.scrape_all(force_all=args.full)


if __name__ == "__main__":
    main()
