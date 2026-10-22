# Alfa Music

Reproductor de música personal para Android: buscás, tocás y suena por streaming desde YouTube Music, sin anuncios ni descargas.

## Descargar

**https://github.com/alfonsocarabajal-pixel/Alfa-music/releases/latest/download/AlfaMusic.apk**

Este enlace siempre baja la última versión. Al abrir el archivo, Android puede pedir permitir "instalar apps desconocidas" desde el navegador: hay que permitirlo para instalar. Las versiones nuevas se instalan encima sin perder nada.

## Acceso

Al abrir la app por primera vez pide un **código de invitación** (una palabra con dos números, como `LUNA27`). Cada código sirve para un solo teléfono. El administrador los crea, comparte y revoca desde la pestaña **Invitaciones**, que solo aparece en su teléfono.

**Karaoke:** pestaña que busca versiones karaoke/instrumentales de YouTube y muestra la letra sincronizada en grande (desde LRCLIB), con ajuste de desfase. Solo la ven el administrador y las personas a las que se la active (🎤 en Invitaciones).

**Versiones:** en Invitaciones, el administrador ve qué versión tiene instalada cada persona y cuándo usó la app por última vez.

**Grupos:** cada invitación pertenece a un grupo (por ejemplo, Alfas o Familia) y cada grupo tiene sus propias playlists. Los usuarios no ven en qué grupo están; el administrador crea grupos al invitar, mueve personas entre grupos y cambia de grupo desde la pestaña Playlists.

## Cómo se usa

- **Tocar una canción** la reproduce y arranca la **radio automática**, que agrega canciones parecidas cuando la cola se está por terminar.
- **Buscador con sugerencias**: mientras escribís aparecen nombres sugeridos (corrigen errores de escritura); tocá uno para buscarlo.
- **Botón ≡+ (agregar a playlist)**: en los resultados y en la cola; suma la canción a cualquier playlist del grupo o crea una nueva con ella.
- **Botón +**: agrega la canción a la cola, antes de las sugerencias de la radio.
- **Botón de cola** (abajo a la derecha): tocá una canción para saltar a ella, ✕ para quitarla, y el botón 🔀 activa o desactiva el **modo aleatorio**.
- **Pestaña Playlists**: las playlists de todo el grupo. Cualquiera puede reproducirlas, renombrarlas, quitarles canciones o borrarlas.
- **Botón 💾 en la cola**: guarda la cola como playlist con nombre. Si cargaste una playlist y le agregaste canciones, con el mismo nombre se actualiza.
- Al abrir la app aparece la última cola de ese teléfono, en pausa donde había quedado.
- Sigue sonando con la pantalla apagada, con controles en la notificación, la pantalla bloqueada y los auriculares.

## Mantenimiento

- **Actualizaciones dentro de la app:** al abrirla, si hay una versión nueva aparece un aviso «Actualizar». La app la descarga e instala encima sin perder nada. La primera vez Android pide permitir que Alfa Music instale actualizaciones.

- **Todos los días a las 6:15 (hora de Uruguay)** GitHub revisa si NewPipe publicó un extractor nuevo. Solo si hay uno, compila, prueba y publica una versión nueva; si no, no pasa nada.
- Si un día deja de reproducir: **Actions → Compilar APK → Run workflow**, y reinstalar desde el enlace de arriba.

Proyecto de uso personal. Usa la API interna de YouTube (no oficial): puede dejar de funcionar cuando YouTube cambia algo, y en general se arregla recompilando.

## Detalles técnicos

- Kotlin + Jetpack Compose, Media3 (ExoPlayer + MediaSession) y NewPipe Extractor.
- Sin base de datos, sin login y sin librerías extra de imágenes ni de red.
- El audio se resuelve recién al reproducir cada canción (`ytm://ID` → URL directa), así los enlaces nunca vencen en la cola.
- Las playlists compartidas se guardan en Firebase Firestore (API REST, sin SDK). El proyecto y el código del grupo están cifrados en `config/nube.env.enc`; antes de publicar, la compilación prueba escribir, leer y borrar en Firebase.
- La clave de firma está cifrada en `keystore/alfa.jks.enc`; la compilación la descifra con el secreto `ALFA_KS_PASS`.
