"""Busca emisoras de Uruguay en radio-browser.info y prueba si cada transmisión responde."""
import json, sys, urllib.request, concurrent.futures as cf

UA = {"User-Agent": "AlfaMusic/1.0"}

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.loads(r.read())

def probe(url):
    try:
        req = urllib.request.Request(url, headers={**UA, "Icy-MetaData": "0"})
        with urllib.request.urlopen(req, timeout=12) as r:
            data = r.read(32768)
            return {"ok": len(data) > 4000, "status": r.status,
                    "type": r.headers.get("Content-Type", ""), "bytes": len(data), "final": r.geturl()}
    except Exception as e:
        return {"ok": False, "error": str(e)[:120]}

stations = []
for host in ("de1", "fi1", "nl1", "at1"):
    try:
        stations = get(f"https://{host}.api.radio-browser.info/json/stations/bycountrycodeexact/UY"
                       "?order=clickcount&reverse=true&limit=500")
        break
    except Exception as e:
        print("fallo", host, e)

def one(s):
    u = s.get("url_resolved") or s.get("url")
    return {"name": s["name"].strip(), "url": u, "codec": s.get("codec"), "bitrate": s.get("bitrate"),
            "tags": s.get("tags"), "state": s.get("state"), "homepage": s.get("homepage"),
            "favicon": s.get("favicon"), "clicks": s.get("clickcount"), "votes": s.get("votes"),
            "lastcheckok": s.get("lastcheckok"), "probe": probe(u)}

with cf.ThreadPoolExecutor(24) as ex:
    out = list(ex.map(one, stations))

extra = sys.argv[1:]  # URLs sueltas para probar
out += [{"name": "EXTRA", "url": u, "probe": probe(u)} for u in extra]
json.dump(out, open("radios-candidatas.json", "w"), ensure_ascii=False, indent=1)
print(len(out), "emisoras,", sum(o["probe"]["ok"] for o in out), "responden")
