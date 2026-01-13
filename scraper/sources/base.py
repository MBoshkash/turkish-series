"""Base Scraper Class - كل السكرابرز هترث منه"""

from abc import ABC, abstractmethod
from typing import Dict, List, Optional, Any
import requests
from bs4 import BeautifulSoup
import cloudscraper
import time
import re
import os


class BaseScraper(ABC):
    """Base class for all scrapers"""

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

        # Proxy support from environment
        self.proxies = None
        proxy_url = os.environ.get('SCRAPER_PROXY')
        if proxy_url:
            self.proxies = {
                'http': proxy_url,
                'https': proxy_url
            }
            print(f"[BaseScraper] Using proxy: {proxy_url[:30]}...")

    def get_page(self, url: str, retries: int = 3) -> Optional[BeautifulSoup]:
        """Fetch a page and return BeautifulSoup object"""
        for attempt in range(retries):
            try:
                response = self.scraper.get(
                    url,
                    headers=self.headers,
                    timeout=30,
                    proxies=self.proxies
                )
                response.raise_for_status()
                # Force UTF-8 encoding for Arabic text
                response.encoding = 'utf-8'
                return BeautifulSoup(response.text, 'lxml')
            except Exception as e:
                print(f"[{self.source_name}] Attempt {attempt + 1} failed for {url}: {e}")
                if attempt < retries - 1:
                    time.sleep(2 ** attempt)  # Exponential backoff
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
