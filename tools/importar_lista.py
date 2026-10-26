"""
Importa una lista de temas (listas/*.txt, formato "Canción — Artista") como playlist
en TODOS los grupos de Alfa Music (Firebase Firestore, API REST).
Busca cada tema en YouTube con yt-dlp (solo búsqueda, sin descargar nada).
Uso (en GitHub Actions): python tools/importar_lista.py listas/bajada.txt
Variables de entorno: FS_PROJECT, FS_GROUP, FS_ADMIN.
"""
import json, os, re, sys, time, unicodedata, urllib.request, urllib.error
import yt_dlp

API = "https://firestore.googleapis.com/v1/"
P, G, ADMIN = os.environ["FS_PROJECT"], os.environ["FS_GROUP"], os.environ["FS_ADMIN"]
ROOT = f"projects/{P}/databases/(default)/documents"
GROUP_PATH = f"{ROOT}/grupos/{G}"


def http(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read() or b"{}")


def norm(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    s = re.sub(r"\s+-\s+.*$", "", s)           # " - Remasterizado 2008", " - En Vivo", ...
    s = re.sub(r"\(.*?\)", "", s)               # "(feat. ...)"
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def clean_title(t):
    t = re.sub(r"\s+-\s+(Remaster\w*|Remasterizado|En Vivo|Edit|Radio Version|Single Mix|Spanish Version|"
               r"Spanglish|English Version|Original.*|Nueva Versi.n.*|Las Ventas.*).*$", "", t, flags=re.I)
    return t.strip()


def read_list(path):
    name, items, seen = "Playlist", [], set()
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        if line.startswith("#"):
            m = re.match(r"#\s*nombre:\s*(.+)", line)
            if m:
                name = m.group(1).strip()
            continue
        title, _, artists = line.partition(" — ")
        first_artist = artists.split(",")[0].strip()
        key = norm(title) + "|" + norm(first_artist)
        if key in seen:
            continue
        seen.add(key)
        items.append((clean_title(title), artists.strip(), first_artist))
    return name, items


def search(ydl, title, artist):
    for q in (f"{artist} - {title}", f"{title} {artist}"):
        try:
            res = ydl.extract_info(f"ytsearch5:{q}", download=False)
        except Exception as e:  # noqa
            print(f"  error buscando '{q}': {e}")
            time.sleep(2)
            continue
        for e in res.get("entries") or []:
            d = e.get("duration") or 0
            if e.get("id") and len(e["id"]) == 11 and 60 <= d <= 900:
                return e["id"], int(d)
    return None, 0


def main():
    path = sys.argv[1]
    name, items = read_list(path)
    print(f"Lista '{name}': {len(items)} temas sin repetir")

    songs, missing = [], []
    with yt_dlp.YoutubeDL({"quiet": True, "extract_flat": True, "skip_download": True, "noplaylist": True}) as ydl:
        for i, (title, artists, first) in enumerate(items, 1):
            vid, dur = search(ydl, title, first)
            if not vid:
                missing.append(f"{title} — {artists}")
                print(f"[{i}] NO ENCONTRADO: {title} — {artists}")
                continue
            print(f"[{i}] {title} — {artists} -> {vid}")
            songs.append({"mapValue": {"fields": {
                "id": {"stringValue": vid},
                "t": {"stringValue": title},
                "a": {"stringValue": artists},
                "d": {"integerValue": str(dur)},
                "th": {"stringValue": f"https://i.ytimg.com/vi/{vid}/mqdefault.jpg"},
                "art": {"stringValue": f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"},
            }}})

    # Todos los grupos: el principal + los creados por el administrador.
    groups = {"principal"}
    res = http("GET", f"{API}{GROUP_PATH}/admins/{ADMIN}/grupos?pageSize=300")
    for d in res.get("documents", []):
        groups.add(d["name"].rsplit("/", 1)[-1])

    doc_id = re.sub(r"[^a-z0-9]+", "-", norm(name)).strip("-") or "lista"
    fields = {
        "nombre": {"stringValue": name},
        "autor": {"stringValue": "Denis"},
        "actualizada": {"integerValue": str(int(time.time() * 1000))},
        "canciones": {"arrayValue": {"values": songs}},
    }
    for g in sorted(groups):
        coll = f"{GROUP_PATH}/playlists" if g == "principal" else f"{GROUP_PATH}/equipos/{g}/playlists"
        http("POST", f"{API}{ROOT}:commit", {"writes": [{"update": {"name": f"{coll}/{doc_id}", "fields": fields}}]})
        print(f"Playlist '{name}' guardada en el grupo {g}")

    summary = f"'{name}': {len(songs)} de {len(items)} temas encontrados, en {len(groups)} grupos."
    if missing:
        summary += " No encontrados: " + " | ".join(missing)
    print("::notice::" + summary)


if __name__ == "__main__":
    main()
