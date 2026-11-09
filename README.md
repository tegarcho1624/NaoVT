# NaoVT — Fase 14 (IA local: chat con Nao)

Mascota virtual Naomi en Android (Jetpack Compose, tema oscuro).

- Avatar por capas: `ui/avatar/NaoVTAvatar.kt`
- Fondos y habitaciones: `ui/room/`
- Pantalla e interacción (toque → cariño 2 s): `ui/NaoScreen.kt`
- Voz (TTS nativo, es-MX/es-ES): `voice/NaoVoice.kt` — pitch y velocidad en su `companion object`
- Frases: `dialog/NaoPhrases.kt`
- Boca al hablar: `ui/avatar/TalkingMouth.kt`
- Baúl: `ui/vault/` (pantalla, cuadrícula, detalle, diálogo) y `data/` (copia de fotos + JSON en SharedPreferences)
- Modo Flotante (pellizca para cambiar el tamaño, 100–300 dp): `overlay/` (servicio en primer plano, vista flotante con las capas, permisos y notificación)
- Menú radial (mantener presionado a Nao flotante): `overlay/RadialMenuView.kt`; opciones en `buildMenuItems()` del servicio
- Alertas: `alerts/` (AlarmManager, receptores, notificación y voz), `data/AlertRepository.kt` y `ui/alerts/` (pantalla Mis Alertas)
- Frases: `data/PhraseRepository.kt` (JSON en SharedPreferences) y `ui/phrases/` (pantalla Frases de Nao)
- Modo Estudio: `pomodoro/` (estado, AlarmManager, notificaciones, receptores) y `ui/pomodoro/` (pantalla)
- Ajustes de Voz (volumen, tono, velocidad): `voice/VoicePreferences.kt` y `ui/voice/VoiceSettingsScreen.kt`; `NaoVoice` los lee cada vez que habla
- IA local (Llamatik / llama.cpp): `llm/` (modelo, generación por streaming, oraciones para la voz) y `ui/chat/` (pantalla de chat). El modelo .gguf se importa desde la app o va en `assets/models/` (ver `LEEME.txt`)
- Assets en `app/src/main/res/drawable/`

APK: sube a GitHub y revisa Actions → *Build APK* → artefacto `NaoVT-debug-apk`.
