# Cluster Launcher ⚡

> **Launcher automotivo estilo Android Auto Coolwalk, 100% offline, com player de música integrado e mapas vetoriais/raster MBTiles — projetado sob medida para centrais multimídia Android.**

Parte do ecossistema **Cluster**. Acesse o portal oficial em [joaohouto.github.io/clusterlauncher](https://joaohouto.github.io/clusterlauncher).

---

## 🏎️ Destaques e Ergonomia Automotiva

* **Cockpit Unificado estilo Android Auto:** Barra lateral vertical com botões táteis ampliados (Apps, Quick Dock configurável, Bluetooth e Configurações), card de mídia em tela cheia com gestos (swipe para trocar faixa, toque duplo para play/pause) e mapa offline integrado.
* **Player de Música Integrado (Media3 / ExoPlayer):**
  * Suporte a `.mp3`, `.flac`, `.wav`, `.m4a`, `.aac`, `.ogg` e `.opus` em armazenamento interno e pendrives USB.
  * **Normalização de Volume EBU R128** + **Equalizador de 5 Bandas** com presets automotivos (*Flat, Graves, Rock, Pop, Eletrônica, Vocal, Personalizado*).
  * **Letras sincronizadas (`.lrc`)** com ajuste de offset e sincronização exata de áudio/tela sem deriva acumulada em playlists longas.
  * **Controle Universal de Mídia:** Intercepta e controla também players externos (Spotify, YouTube Music, VLC, etc.) via `MediaSessionManager`.
* **Mapas Offline MBTiles (Zero-Copy para arquivos grandes):**
  * Motor duplo: **MapLibre Native** para mapas vetoriais `.pbf` (com servidor loopback otimizado e leitura direta de arquivos 300MB–2GB+) e **osmdroid** para mapas raster.
  * Visão 3D/2D no Cockpit e visualização **de cima (top-down)** ao ampliar o mapa em tela cheia.
* **Estética Deep Metallic (`#0B0C0E`) & Escala de UI:** Temas de destaque automotivos, escala configurável (até 120%+) e inicialização escura instantânea (zero flash branco).
* **Auto-Update OTA:** Verificação e instalação de novas versões diretamente via GitHub Releases.

---

## 📦 Download do APK

Baixe a versão otimizada mais recente na aba de [Releases](https://github.com/joaohouto/clusterlauncher/releases/latest).

---

## 🛠️ Tecnologias Utilizadas

* **UI:** Jetpack Compose com Material 3
* **Áudio & Mídia:** AndroidX Media3 (ExoPlayer + MediaSession), EBU R128, Equalizer API
* **Mapas Offline:** MapLibre Native + osmdroid + LocalMBTilesServer (SQLite mmap)
* **Persistência:** Room + Jetpack DataStore Preferences
* **Linguagem & Tooling:** Kotlin 2.2 / Gradle 9 / Minificação R8 ProGuard
