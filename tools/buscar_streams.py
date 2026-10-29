"""Lee páginas oficiales de radios y extrae direcciones de transmisión candidatas; las prueba."""
import json, re, sys, urllib.request, urllib.parse, concurrent.futures as cf

UA_WEB = {"User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"}
UA_PLAYER = {"User-Agent": "ExoPlayerLib/2.19.1", "Icy-MetaData": "0"}
PAGES = [l.split() for l in open("tools/radios-paginas.txt", encoding="utf-8") if l.strip() and not l.startswith("#")]
PAT = re.compile(r"""https?:[\\/]+[^\s"'<>()\\]+?(?:\.m3u8|\.mp3|\.aac|\.pls|\.m3u|/stream\b|/live\b|/;|:\d{4,5}/[^\s"'<>]*|streamtheworld[^\s"'<>]*|mediatique[^\s"'<>]*)""", re.I)

def fetch(url, n=600000, ua=UA_WEB):
    with urllib.request.urlopen(urllib.request.Request(url, headers=ua), timeout=15) as r:
        return r.read(n).decode("utf-8", "ignore"), r.geturl()

def probe(url):
    out = {}
    for name, ua in (("player", UA_PLAYER),):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=ua), timeout=12) as r:
                data = r.read(32768)
                ct = r.headers.get("Content-Type", "")
                out = {"ok": len(data) > 4000 and "html" not in ct, "type": ct, "bytes": len(data), "final": r.geturl(),
                       "head": data[:120].decode("latin1") if ("mpegurl" in ct or "text" in ct) else ""}
        except Exception as e:
            out = {"ok": False, "error": str(e)[:100]}
    return out

def scan(entry):
    key, page = entry[0], entry[1]
    found = set(entry[2:])
    try:
        html, final = fetch(page)
        found |= {u.replace("\\/", "/") for u in PAT.findall(html)}
        scripts = re.findall(r"""<(?:script|iframe)[^>]+src=["']([^"']+)""", html, re.I)[:25]
        for s in scripts:
            s = urllib.parse.urljoin(final, s)
            if any(x in s for x in ("google", "facebook", "jquery", "gtag", "analytics")):
                continue
            try:
                js, _ = fetch(s, 400000)
                found |= {u.replace("\\/", "/") for u in PAT.findall(js)}
            except Exception:
                pass
    except Exception as e:
        print(key, "no se pudo leer", page, e)
    found = {u.rstrip(".,;") if not u.endswith("/;") else u for u in found}
    found = {u for u in found if not re.search(r"\.(js|css|png|jpg|svg|webp)(\?|$)", u, re.I)}
    return key, page, {u: probe(u) for u in sorted(found)[:40]}

with cf.ThreadPoolExecutor(12) as ex:
    res = list(ex.map(scan, PAGES))
json.dump([{"radio": k, "page": p, "candidatas": c} for k, p, c in res], open("streams-oficiales.json", "w"),
          ensure_ascii=False, indent=1)
for k, p, c in res:
    print(k, sum(v["ok"] for v in c.values()), "de", len(c))
