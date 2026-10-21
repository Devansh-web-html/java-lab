#!/usr/bin/env python3
"""
Fetch free-to-reuse product photos for ShopKart from Wikimedia Commons.

  python tools/fetch_product_images.py            # fetch photos for products that have none yet
  python tools/fetch_product_images.py --force    # re-fetch everything
  python tools/fetch_product_images.py 3 9        # only product ids 3 and 9

- Uses the public MediaWiki API: no API key, no paid service, no scraping of web pages.
- Keeps only files licensed for commercial reuse: CC0, public domain, CC BY, CC BY-SA.
  Non-commercial (NC) licences are skipped.
- Writes assets/products/<id>.<jpg|png> and a credit file <id>.credit.txt next to it.
- Products with no acceptable match are left alone; ShopKart shows its built-in illustration.
- Re-running is safe. New products in ShopKart.java are picked up automatically.
- Standard library only (Python 3.8+).
"""
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "ShopKart.java"
OUT = ROOT / "assets" / "products"
API = "https://commons.wikimedia.org/w/api.php"
# Wikimedia asks for a descriptive User-Agent with contact details. Put your own email here.
USER_AGENT = "ShopKart-student-project/1.0 (replace-with-your-email@example.com)"
STOP_WORDS = {"and", "the", "for", "with", "set", "of", "pack"}
PRODUCT_RE = re.compile(r'new Product\((\d+), "([^"]+)", "([^"]+)", (\d+), "[^"]*", "[^"]*"\)')


def read_products(text=None):
    """Returns (id, name, category) for each Product line in ShopKart.java."""
    text = SOURCE.read_text(encoding="utf-8") if text is None else text
    return [(int(i), name, cat) for i, name, cat, _price in PRODUCT_RE.findall(text)]


def api_get(params):
    query = dict(params, format="json", formatversion="2")
    request = urllib.request.Request(API + "?" + urllib.parse.urlencode(query),
                                     headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def search_commons(term):
    data = api_get({
        "action": "query", "generator": "search", "gsrsearch": term + " filetype:bitmap",
        "gsrnamespace": 6, "gsrlimit": 10, "prop": "imageinfo",
        "iiprop": "url|mime|extmetadata", "iiurlwidth": 800,
    })
    return data.get("query", {}).get("pages", [])


def acceptable_license(meta):
    lic = meta.get("LicenseShortName", {}).get("value", "").lower()
    if "nc" in lic:
        return False
    return lic.startswith(("cc0", "public domain", "pd", "cc by"))


def pick_match(pages, base_name):
    """Returns the first result whose title contains every significant word of the product name."""
    words = [w for w in re.findall(r"[a-z0-9]+", base_name.lower()) if len(w) > 2 and w not in STOP_WORDS]
    if not words:
        return None
    for page in pages:
        info = (page.get("imageinfo") or [{}])[0]
        if info.get("mime") not in ("image/jpeg", "image/png") or "thumburl" not in info:
            continue
        title = page.get("title", "").lower()
        if not all(w in title for w in words):
            continue
        if not acceptable_license(info.get("extmetadata", {})):
            continue
        return info, page
    return None


def credit_line(info, page):
    meta = info.get("extmetadata", {})
    artist = html.unescape(re.sub(r"<[^>]+>", "", meta.get("Artist", {}).get("value", ""))).strip()
    license_name = meta.get("LicenseShortName", {}).get("value", "")
    title = re.sub(r"^File:", "", page.get("title", ""))
    title = re.sub(r"\.[A-Za-z]+$", "", title)
    return (f"Photo: {title} · {artist or 'Wikimedia Commons contributor'} · {license_name} "
            f"· via Wikimedia Commons ({info.get('descriptionurl', '')})")


def download(url, destination):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        destination.write_bytes(response.read())


def main(argv):
    force = "--force" in argv
    only = {int(a) for a in argv if a.isdigit()}
    OUT.mkdir(parents=True, exist_ok=True)
    found = missing = 0

    for pid, name, cat in read_products():
        if only and pid not in only:
            continue
        if not force and any((OUT / f"{pid}.{ext}").exists() for ext in ("jpg", "png")):
            print(f"[skip ] {pid:>2} {name}")
            continue

        base = name.split(",")[0].strip()          # "Basmati Rice, 5 kg" -> "Basmati Rice"
        match = None
        try:
            for term in (base, f"{base} {cat}"):   # second try adds the category
                match = pick_match(search_commons(term), base)
                if match:
                    break
                time.sleep(0.5)
        except Exception as error:                 # network or API problems: skip, don't crash
            print(f"[error] {pid:>2} {name}: {error}")
            missing += 1
            continue

        if not match:
            print(f"[none ] {pid:>2} {name}")
            missing += 1
            continue

        info, page = match
        ext = "png" if info["mime"] == "image/png" else "jpg"
        for old in OUT.glob(f"{pid}.*"):
            old.unlink()
        download(info["thumburl"], OUT / f"{pid}.{ext}")
        (OUT / f"{pid}.credit.txt").write_text(credit_line(info, page), encoding="utf-8")
        print(f"[ok   ] {pid:>2} {name} -> {page.get('title')}")
        found += 1
        time.sleep(0.5)

    print(f"\nDone: {found} photo(s) saved, {missing} product(s) without a match (illustration shown).")


if __name__ == "__main__":
    main(sys.argv[1:])
