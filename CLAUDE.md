# Instrucciones para Claude en este proyecto

## Idioma (obligatorio)
- Respondé **siempre en español** (rioplatense, voseo) al usuario, en **todos** los mensajes: respuestas, avisos de fin de tarea, resúmenes y reportes de compilación.
- Esto aplica **especialmente** cuando la respuesta sigue a una notificación automática en inglés (fin de una tarea en segundo plano, resultado de GitHub Actions, salida de herramientas). Esas notificaciones no son mensajes del usuario: el idioma de la respuesta sigue siendo el español.
- Antes de enviar cualquier mensaje, verificá que esté en español.

## Proyecto
- Alfa Music: app Android (Kotlin + Compose + Media3 + NewPipe Extractor) con playlists, grupos, invitaciones y karaoke en Firebase Firestore (API REST).
- Compilación y publicación: GitHub Actions (`.github/workflows/compilar.yml`). La rama `prueba` compila sin publicar; `main` publica.
- Para cambios que no deben generar una versión nueva, usar `[skip ci]` en el mensaje del commit.
