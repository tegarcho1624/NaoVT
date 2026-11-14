# NaoVT — Fase 23 (Menú de Gestos)

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
- Mini chat de voz: `llm/SpokenAnswerer.kt` (respuesta solo hablada), `ui/MiniChatPanel.kt` (toque largo sobre Nao) y `overlay/MiniChatPanelView.kt` (botón "Hablar" del menú radial)
- Menú lateral (reemplaza a los botones flotantes): `ui/NaoDrawer.kt` y `ui/NaoScreen.kt`
- Habitaciones: `data/RoomRepository.kt` (JSON en SharedPreferences + copia en filesDir/habitaciones/), `ui/rooms/` (pantalla Gestionar Habitaciones) y `ui/room/` (fondo imagen o video con ExoPlayer)
- Armario y avatar modular (fase 18): el arte nuevo vive en `app/src/main/assets/naomi/<Carpeta>/` (las carpetas del ZIP de arte; agregar un PNG a una carpeta basta para que aparezca en el Armario).
  - `wardrobe/WardrobeSlot.kt`: cada ranura (ojos, boca, camisa...) con su carpeta y su **orden de dibujo (`z`)**; el orden de las entradas es el de las pestañas.
  - `wardrobe/CharacterConfig.kt`: qué archivo lleva Nao en cada ranura (`CharacterConfig.Default` = vestido maid). `wardrobe/WardrobeViewModel.kt` lo comparte con la pantalla principal y lo guarda en SharedPreferences.
  - `wardrobe/WardrobeCatalog.kt` (lee las carpetas) y `wardrobe/WardrobeBitmaps.kt` (carga de capas y miniaturas con caché).
  - Gestor de Capas (fase 19): `ui/wardrobe/LayerPanel.kt` (panel lateral con arrastrar y soltar). El orden manual vive en `CharacterConfig.order` (se guarda con `WardrobeViewModel.reorder`); `WardrobeSlot.z` solo es el orden inicial.
  - Assets propios (fase 20): el "+" de cada categoría importa un PNG/JPEG con PickVisualMedia a `filesDir/assets_personalizados/<categoría>/` (`wardrobe/CustomAssetStore.kt`; registro y orden en JSON en SharedPreferences `naovt_wardrobe_items`). Los importados se identifican como `custom/<archivo>`; los nativos nunca se pueden borrar. En la cuadrícula: tocar = equipar, mantener y arrastrar = reordenar (solo orden visual), mantener sin arrastrar en un importado = eliminar.
  - Outfits (fase 21): `wardrobe/Outfit.kt` (modelo + JSON en SharedPreferences `naovt_outfits`: nombre, asset por categoría y orden de capas). `ui/wardrobe/OutfitsSection.kt`: botón "Guardar Outfit" y fila "Mis Outfits" (tocar = vestir, mantener y arrastrar = reordenar, mantener sin arrastrar = eliminar; el orden se guarda con la lista JSON). Al vestir un outfit, los assets que ya no existen se ignoran (`WardrobeViewModel.resolveOutfit`).
  - Gestos (fase 23): `gestures/GestureSettings.kt` (ojos/boca de Pestañeo, Hablar y Beso + frecuencia y duración del parpadeo; SharedPreferences `naovt_gestures`; `null` = usar los ojos/boca del Armario). `ui/gestures/GesturesScreen.kt` es la pantalla; `rememberBlink` y `NaoLayeredAvatar` leen esos ajustes en tiempo real. El ícono de mano (`ui/icons/TouchAppIcon.kt`) es un vector propio porque solo se incluye `material-icons-core`. El Modo Flotante (overlay) aún no usa estos ajustes.
  - Fase 24: nombres reales de assets importados (`CustomAssetStore.realName`; si la galería solo da un número, la app pide el nombre), "Cambiar nombre" en Mis Outfits (mantener sin arrastrar), vista previa estática de cada outfit (`wardrobe/OutfitPreviews.kt`: PNG en `filesDir/previews/<id>.png`, se borra con el outfit) y "Gestos de Conjuntos" (cuadrícula 2x2 en `ui/gestures/GesturesScreen.kt`). `Outfit.gestures: CharacterGestures?` guarda los gestos propios; `gestures/EffectiveGestures.kt` elige los del outfit puesto o, si no tiene, los globales (usado por `NaoScreen` y `rememberBlink`).
  - `ui/avatar/NaoLayeredAvatar.kt`: dibuja las capas (pantalla principal y vista previa); `ui/wardrobe/WardrobeScreen.kt`: pantalla del Armario.
  - Pendiente: el Modo Flotante, las alertas y `NaoHeadBadge` siguen con el arte anterior de `res/drawable/` (no se borró).
- Assets anteriores en `app/src/main/res/drawable/`

APK: sube a GitHub y revisa Actions → *Build APK* → artefacto `NaoVT-debug-apk`.
