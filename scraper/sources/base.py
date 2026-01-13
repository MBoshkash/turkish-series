"""Base Scraper Class - كل السكرابرز هترث منه"""

from abc import ABC, abstractmethod
from typing import Dict, List, Optional, Any
import requests
from bs4 import BeautifulSoup
import cloudscraper
import time
import re
import os
import random
from pathlib import Path


class BaseScraper(ABC):
    """Base class for all scrapers"""

    # Shared proxy list across all scraper instances
    _proxy_list: List[str] = []
    _working_proxy: Optional[str] = None
    _proxy_loaded: bool = False

    def __init__(self):
        self.scraper = cloudscraper.create_scraper(
            browser={
                'browser': 'chrome',
                'platform': 'windows',
                'mobile': False
            }
        )
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
            'Accept-Language': 'ar,en-US;q=0.7,en;q=0.3',
        }
        self.base_url = ""
        self.source_name = ""

        # Load proxies once
        if not BaseScraper._proxy_loaded:
            self._load_proxies()

    def _load_proxies(self):
        """Load proxy list from file or environment"""
        BaseScraper._proxy_loaded = True

        # First check environment variable for single proxy
        proxy_url = os.environ.get('SCRAPER_PROXY')
        if proxy_url:
            BaseScraper._proxy_list = [proxy_url]
            print(f"[BaseScraper] Using proxy from env: {proxy_url[:30]}...")
            return

        # Then check for proxy list file
        proxy_file = os.environ.get('PROXY_LIST_FILE', 'proxies.txt')

        # Try multiple locations
        possible_paths = [
            Path(proxy_file),
            Path(__file__).parent.parent / proxy_file,
            Path(__file__).parent.parent.parent / proxy_file,
        ]

        for path in possible_paths:
            if path.exists():
                try:
                    with open(path, 'r') as f:
                        proxies = [line.strip() for line in f if line.strip() and not line.startswith('#')]
                    if proxies:
                        BaseScraper._proxy_list = proxies
                        print(f"[BaseScraper] Loaded {len(proxies)} proxies from {path}")
                        return
                except Exception as e:
                    print(f"[BaseScraper] Error loading proxy file {path}: {e}")

        print("[BaseScraper] No proxies configured, using direct connection")

    def _get_proxy(self) -> Optional[Dict[str, str]]:
        """Get a working proxy or try from list"""
        if BaseScraper._working_proxy:
            return {'http': BaseScraper._working_proxy, 'https': BaseScraper._working_proxy}

        if not BaseScraper._proxy_list:
            return None

        # Return first proxy to try
        proxy = BaseScraper._proxy_list[0]
        return {'http': proxy, 'https': proxy}

    def _mark_proxy_failed(self, proxy_url: str):
        """Mark a proxy as failed and try next one"""
        if proxy_url in BaseScraper._proxy_list:
            BaseScraper._proxy_list.remove(proxy_url)
            print(f"[BaseScraper] Removed failed proxy, {len(BaseScraper._proxy_list)} remaining")
        if BaseScraper._working_proxy == proxy_url:
            BaseScraper._working_proxy = None

    def _mark_proxy_working(self, proxy_url: str):
        """Mark a proxy as working"""
        BaseScraper._working_proxy = proxy_url
        print(f"[BaseScraper] Found working proxy: {proxy_url[:30]}...")

    def get_page(self, url: str, retries: int = 3) -> Optional[BeautifulSoup]:
        """Fetch a page and return BeautifulSoup object"""

        # Try with proxies first if available
        if BaseScraper._proxy_list:
            for proxy_url in list(BaseScraper._proxy_list):  # Copy list to iterate
                proxies = {'http': proxy_url, 'https': proxy_url}
                try:
                    response = self.scraper.get(
                        url,
                        headers=self.headers,
                        timeout=15,
                        proxies=proxies
                    )
                    response.raise_for_status()
                    response.encoding = 'utf-8'
                    self._mark_proxy_working(proxy_url)
                    return BeautifulSoup(response.text, 'lxml')
                except Exception as e:
                    print(f"[{self.source_name}] Proxy {proxy_url[:25]}... failed: {str(e)[:50]}")
                    self._mark_proxy_failed(proxy_url)
                    continue

        # Fallback to direct connection
        for attempt in range(retries):
            try:
                response = self.scraper.get(
                    url,
                    headers=self.headers,
                    timeout=30
                )
                response.raise_for_status()
                response.encoding = 'utf-8'
                return BeautifulSoup(response.text, 'lxml')
            except Exception as e:
                print(f"[{self.source_name}] Attempt {attempt + 1} failed for {url}: {e}")
                if attempt < retries - 1:
                    time.sleep(2 ** attempt)
        return None

    def get_text(self, soup: BeautifulSoup, selector: str, default: str = "") -> str:
        """Safely extract text from element"""
        element = soup.select_one(selector)
        return element.get_text(strip=True) if element else default

    def get_attr(self, soup: BeautifulSoup, selector: str, attr: str, default: str = "") -> str:
        """Safely extract attribute from element"""
        element = soup.select_one(selector)
        return element.get(attr, default) if element else default

    @abstractmethod
    def get_series_info(self, url: str) -> Optional[Dict[str, Any]]:
        """Get series information"""
        pass

    @abstractmethod
    def get_episodes_list(self, url: str) -> List[Dict[str, Any]]:
        """Get list of episodes"""
        pass

    @abstractmethod
    def get_episode_servers(self, url: str) -> Dict[str, Any]:
        """Get episode watch/download servers"""
        pass
