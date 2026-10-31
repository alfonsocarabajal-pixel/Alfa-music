"""Diagnóstico: estado de las invitaciones cuyo nombre contiene el texto dado (sin mostrar códigos completos)."""
import json, os, sys, urllib.request, urllib.error, datetime
API = "https://firestore.googleapis.com/v1/"
P, G, A = os.environ["FS_PROJECT"], os.environ["FS_GROUP"], os.environ["FS_ADMIN"]
ROOT = f"projects/{P}/databases/(default)/documents/grupos/{G}"
q = (sys.argv[1] if len(sys.argv) > 1 else "").lower()

def get(path):
    try:
        with urllib.request.urlopen(API + path, timeout=30) as r:
            return 200, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, {}

def val(f, k):
    v = (f or {}).get(k) or {}
    return v.get("stringValue", v.get("integerValue", v.get("booleanValue", "")))

def fecha(ms):
    try:
        ms = int(ms)
        return "-" if ms <= 0 else datetime.datetime.utcfromtimestamp(ms / 1000 - 3 * 3600).strftime("%d/%m %H:%M")
    except Exception:
        return str(ms)

code, res = get(f"{ROOT}/admins/{A}/emitidas?pageSize=300")
print("emitidas:", code, len(res.get("documents", [])))
for d in res.get("documents", []):
    c = d["name"].rsplit("/", 1)[-1]
    f = d.get("fields", {})
    nombre = val(f, "nombre")
    if q and q not in nombre.lower():
        continue
    st, inv = get(f"{ROOT}/invitaciones/{c}")
    fi = inv.get("fields", {})
    linea = (f"{nombre} | código {c[:2]}…{len(c)} letras | creada {fecha(val(f,'creada'))} | "
             f"revocada {fecha(val(f,'revocada'))} | grupo emitida {val(f,'grupo')} | invitación HTTP {st}")
    if st == 200:
        linea += (f" | teléfono {'SÍ' if val(fi,'dispositivo') else 'no'} | modelo {val(fi,'modelo') or '-'} | "
                  f"activada {fecha(val(fi,'activada'))} | versión {val(fi,'version') or '-'} | "
                  f"visto {fecha(val(fi,'visto'))} | grupo {val(fi,'grupo')}")
    print("::notice::" + linea)
