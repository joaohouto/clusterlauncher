# Cluster Launcher ⚡

> **Dashboard de cockpit esportivo e tela inicial de alta performance, 100% offline, projetado sob medida para centrais multimídia automotivas Android.**

Parte do ecossistema **Cluster** (ao lado de [Cluster Player](https://github.com/joaohouto/clusterplayer) e [Cluster Radio](https://github.com/joaohouto/clusterradio)). Acesse o portal oficial em [joaohouto.github.io/clusterlauncher](https://joaohouto.github.io/clusterlauncher).

---

## 🏎️ Destaques e Ergonomia de Cockpit

* **Cockpit Dashboard com Central de Instrumentos (Página 0):** Painel horizontal de alta precisão com Mini Player universal desacoplado, relógio digital RTC de visualização rápida, indicadores de periféricos veiculares (USB/Bluetooth) e Quick Dock tátil fixa.
* **Quick Dock Ergonômica de Acesso Rápido:** Barra fixa inferior com 5 slots configuráveis e botões táteis ampliados (64x64 dp) para operação segura em movimento.
* **App Drawer Fluido a 60 FPS (Página 1):** Grade completa de aplicativos instalados no sistema com transição contínua via pager horizontal, sem engasgos ou requisições desnecessárias.
* **Desacoplamento Universal de Mídia:** Intercepta e controla universalmente qualquer player de áudio ativo no sistema Android (Cluster Player, Spotify, VLC, YouTube Music, reprodutor nativo USB) através do `MediaSessionManager` e `NotificationListenerService`.
* **Operação Estritamente 100% Offline:** Sem telemetria, sem consumo de dados móveis, sem APIs climáticas pesadas e sem dependência de conexão com a internet.
* **Estética Automotiva Deep Metallic (`#0B0C0E`):** Tons metálicos escuros e acetinados, superfícies chanfradas, acentos esportivos (*Needle Red* `#E61924`) e tipografia de alto contraste para leitura perfeita sob sol forte.
* **Ultra-baixo Consumo de Memória e Bateria:** Otimizado para processadores veiculares modestos (Quad-Core Cortex-A7/A53) e memórias restritas (1GB a 2GB de RAM), com decodificação `RGB_565` e isolamento estrito de recomposições no Jetpack Compose.
* **Inicialização Escura Instantânea (Zero Flash Branco):** Tema escuro nativo no XML (`#0B0C0E`) que impede clarões brancos indesejados ao dar a partida no veículo ou ao pressionar o botão Home à noite.

---

## 📦 Download do APK

Baixe a versão otimizada mais recente na aba de [Releases](https://github.com/joaohouto/clusterlauncher/releases/latest) ou conheça a suíte completa no site oficial em [joaohouto.github.io/clusterlauncher](https://joaohouto.github.io/clusterlauncher).

---

## 🛠️ Tecnologias Utilizadas

* **UI:** Jetpack Compose com Material 3 e Automotive Shaders/Gradients
* **Mídia & Controle Universal:** MediaSessionManager & NotificationListenerService
* **Persistência:** Jetpack DataStore Preferences
* **Linguagem & Tooling:** Kotlin 2.2 / Gradle 9 / Minificação R8 ProGuard
