# Instrucciones para Claude en este proyecto

## Idioma (obligatorio)
- Respondé **siempre en español** (rioplatense, voseo) al usuario, en **todos** los mensajes: respuestas, avisos de fin de tarea, resúmenes y reportes de compilación.
- Esto aplica **especialmente** cuando la respuesta sigue a una notificación automática en inglés (fin de una tarea en segundo plano, resultado de GitHub Actions, salida de herramientas). Esas notificaciones no son mensajes del usuario: el idioma de la respuesta sigue siendo el español.
- Antes de enviar cualquier mensaje, verificá que esté en español.

## Proyecto
- Alfa Music: app Android (Kotlin + Compose + Media3 + NewPipe Extractor) con playlists, grupos, invitaciones y karaoke en Firebase Firestore (API REST).
- Compilación y publicación: GitHub Actions (`.github/workflows/compilar.yml`). La rama `prueba` compila sin publicar; `main` publica.
- Para cambios que no deben generar una versión nueva, usar `[skip ci]` en el mensaje del commit.

## Cambios guardados sin publicar (salen con la próxima versión)
El usuario pidió no generar versiones por cambios chicos: se suben a `main` con `[skip ci]` y se publican junto con el próximo cambio que sí genere versión.
- Invitaciones: ocultar las bloqueadas y ordenar por último uso.
- Registro de errores en Firebase (`grupos/G/errores`) + pantalla «Errores» en Invitaciones + mensajes de activación detallados. Requiere la regla de `errores` en Firebase (la compilación avisa si falta).
- Volumen parejo entre canciones (botón en la cola, activado por defecto, Android 9+).
