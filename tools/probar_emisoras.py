"""Prueba que cada emisora precargada (app/src/main/assets/emisoras.json) esté transmitiendo.
No frena la compilación: deja un aviso por cada emisora que no responde."""
import json, urllib.request, concurrent.futures as cf

UA = {"User-Agent": "ExoPlayerLib/2.19.1", "Icy-MetaData": "0"}
st = json.load(open("app/src/main/assets/emisoras.json", encoding="utf-8"))

def probe(s):
    try:
        with urllib.request.urlopen(urllib.request.Request(s["url"], headers=UA), timeout=15) as r:
            data, ct = r.read(32768), r.headers.get("Content-Type", "")
            return s, len(data) > 4000 and "html" not in ct, ct
    except Exception as e:
        return s, False, str(e)[:80]

with cf.ThreadPoolExecutor(16) as ex:
    res = list(ex.map(probe, st))
bad = [(s, info) for s, ok, info in res if not ok]
for s, info in bad:
    print(f"::warning::Emisora sin respuesta: {s['nombre']} ({s['banda']} {s['frecuencia']}) — {info}")
print(f"::notice::Emisoras: {len(st) - len(bad)} de {len(st)} transmitiendo")
