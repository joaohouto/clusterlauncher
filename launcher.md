# Especificação Técnica: Automotive Cluster Launcher (Android)

Documento mestre de arquitetura, design, ergonomia e engenharia para desenvolvimento da tela inicial (Home/Launcher) customizada para centrais multimídia automotivas Android.

> [!NOTE]
> Este documento incorpora todas as lições aprendidas, padrões de baixo consumo de recursos e diretrizes de alta performance consolidadas no desenvolvimento do **Cluster Player**, otimizadas para processadores modestos (Quad-Core Cortex-A7/A53), memórias limitadas (1GB a 2GB de RAM), armazenamento eMMC e ausência total de conexão à internet.

---

## 1. Visão Geral do Produto

* **Propósito:** Substituir a Home genérica/limitada de fábrica da central multimídia por uma interface automotiva de cockpit premium, rápida, responsiva e ergonômica para operação veicular.
* **Premissa Operacional:** Funcionamento estritamente **100% offline** (sem requisições de rede, sem telemetria, sem APIs meteorológicas e sem dependência de internet).
* **Independência de Mídia (Desacoplamento Universal):** O launcher é 100% independente do Cluster Player. Ele consome o `MediaSessionManager` universal do sistema Android via `NotificationListenerService`, interceptando e comandando qualquer player ativo (Cluster Player, Spotify, VLC, YouTube Music, reprodutor nativo USB, etc.) sem exigir alteração de código ou integração proprietária.
* **Navegação Contínua:** 2 páginas horizontais contínuas via `HorizontalPager`:
  * **Página 0 (Cockpit Dashboard):** Central de instrumentos com Mini Player universal, relógio digital RTC de alta precisão, indicadores de periféricos (USB/Bluetooth) e a Quick Dock fixa.
  * **Página 1 (App Drawer):** Grade completa e fluida de todos os aplicativos instalados no sistema.
* **Estética Visual:** *Automotive Cluster UI* — tons metálicos profundos e acetinados, molduras chanfradas, iluminação de instrumentos e acentos em vermelho esportivo (`#E61924`), compartilhando a exata identidade visual do Cluster Player.
* **Zero Flash Branco (Cold Start Escuro):** Inicialização instantânea com tema nativo escuro em XML (`#0B0C0E`), eliminando clarões brancos ao ligar a chave do veículo ou ao pressionar o botão Home à noite.

---

## 2. Decisões de Escopo, Ergonomia e Performance

### 2.1 Decisões de Escopo Fechadas
* **Sem Velocímetro Inicial:** O painel foca na gestão de mídias, relógio de bordo e acesso rápido aos aplicativos, sem leituras de GPS ou OBD-II na primeira fase (evita consumo excessivo de bateria, CPU e dependência de sinal de satélite).
* **Sem Controle de Brilho via App:** O controle de iluminação permanece a cargo da multimídia/faróis do carro. A interface opera em tema escuro contínuo (*Dark Metallic*).
* **Desacoplamento de Mídia:** Nenhuma dependência cruzada de código. O launcher consome apenas APIs padrão do framework Android (`MediaSessionManager`, `MediaController`).

### 2.2 Paleta de Cores e Materiais
* **Fundo Principal (Deep Metallic):** `#0B0C0E` a `#121316` (evita pretos OLED vazios; simula o fundo fosco de um mostrador automotivo).
* **Superfícies e Cartões:** Gradiente sutil de `#181A1F` para `#121316` com bordas chanfradas em `#262930`.
* **Frisos Metálicos & Separadores:** `#2E323A` a `#3A3F47`.
* **Acento Esportivo (Needle Red):** `#E61924` (estados ativos, foco de toque e detalhes dos widgets).
* **Tipografia Primária:** `#FFFFFF` (pesos *Medium* e *Bold* para leitura à distância com contraste máximo).
* **Tipografia Secundária:** `#9A9DA6` (artista, datas e legendas de periféricos).

### 2.3 Ergonomia e Condução Segura
* **Área de Toque Mínima (Hitbox):** Mínimo de **64x64 dp** em todos os botões de ação e atalhos na Quick Dock.
* **Capa de Álbum Quadrada:** Formato 1:1, cantos com raio sutil de `8dp`, sem anéis circulares decorativos que cortam a arte.
* **Tolerância a Toque em Movimento:** Feedback tátil e visual de clique com ripple sutil sem bloquear a thread principal.

### 2.4 Pilares de Alta Performance para Hardware Modesto
1. **Isolamento Total de Recomposições no Jetpack Compose:**
   * O `ClockWidget` roda em Composable com ticker interno desacoplado. A mudança dos minutos **não recomponde** o cartão de mídia, os indicadores ou a Quick Dock.
   * O `UniversalMediaCard` é puramente guiado por eventos (`distinctUntilChanged` no fluxo do `MediaController`). Quando a faixa não muda, **0 recomposições** ocorrem por segundo.
   * As formas (`RoundedCornerShape`) e gradientes de botões devem ser mantidos como constantes ou lembrados com `remember`, evitando alocações contínuas no Garbage Collector.
2. **Gerenciamento Estrito de Memória RAM (Prevenção de OOM):**
   * Configuração de `android:largeHeap="true"` no manifesto.
   * Decodificação de imagens de capa e ícones do App Drawer com **`Bitmap.Config.RGB_565`** (redução de 50% de RAM em comparação a `ARGB_8888`).
   * Limite de cache de memória de imagens em no máximo 15% da RAM disponível.
   * Varredura do `PackageManager` em `Dispatchers.IO` com `LruCache` em memória para ícones de aplicativos.
3. **Preservação de Armazenamento eMMC Flash:**
   * Nenhum polling contínuo em disco.
   * Associação de atalhos da Quick Dock gravada reativamente no DataStore Preferences somente quando o usuário altera um aplicativo.

---

## 3. Estrutura de Telas e Interações

```text
+-----------------------------------------------------------------------------------+
|  [ WIDGET DE MÍDIA UNIVERSAL ]                   |  [ PAINEL DE INSTRUMENTOS ]    |
|  +------------+  Título da Faixa                 |   16:30                        |
|  | Capa 1:1   |  Artista / Álbum                 |   Quarta-feira, 20 de Setembro |
|  | 120x120 dp |  -----------------------------   |                                |
|  +------------+  [ |< ]     [ >/|| ]     [ >| ]  |   [USB OK]        [BT OK]      |
+-----------------------------------------------------------------------------------+
|                            QUICK DOCK FIXA (5 SLOTS TÁTEIS)                       |
|   [ MÚSICA ]     [ GPS ]         [ RÁDIO ]       [ TELEFONE ]     [ AJUSTES ]     |
+-----------------------------------------------------------------------------------+
```

### 3.1 Página 0: Cockpit Dashboard (Tela Principal)

#### A. Widget de Mídia Universal (Lado Esquerdo - 55% da largura)
* **Capa:** Quadrada com cantos arredondados (8dp), decodificada em resolução adaptativa (máx 256x256 RGB_565).
  * Exibe bitmap ou URI fornecido pelo `MediaMetadata` da sessão ativa do Android.
  * Fallback metálico escuro com ícone de nota musical quando ocioso.
* **Metadados:** Título da música (20sp Bold) e Artista/Álbum (15sp SemiBold). Quando nenhuma mídia estiver ativa, exibe *"Nenhuma mídia em reprodução"*.
* **Controles:** Botões metálicos tácteis largos (mínimo 56x56 dp): `Anterior (<)`, `Play/Pause ( ▶ / || )`, `Próximo (>)`.
* **Ação de Toque:** Toque na capa ou no título dispara a `Intent` de abertura do player dono da sessão ativa.
* **Fallback de Permissão de Acesso:** Se a permissão do `NotificationListenerService` ainda não foi concedida pelo usuário, o widget exibe um card amigável:
  * *"Controle de mídia desativado"*
  * *"Toque aqui para conceder permissão de leitura de mídia nas configurações do Android"*
  * Toque direciona diretamente para `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.

#### B. Painel de Instrumentos & Relógio (Lado Direito - 45% da largura)
* **Relógio Digital RTC:** Formato grande (48sp Bold, monospace automotivo), alimentado pelo relógio de hardware interno do Android, 100% offline.
* **Data e Dia:** Formatação regional completa (`Quarta-feira, 20 de Setembro`).
* **Indicadores de Periféricos (Event-Driven):**
  * **Ícone Pen Drive (USB):** Ativo com realce de acento quando há armazenamento USB externo montado; esmaecido quando desconectado.
  * **Ícone Bluetooth:** Indicador de pareamento/conexão com smartphone para chamadas e áudio.

#### C. Quick Dock Fixa (Base da Tela)
Fileira horizontal com 5 slots táteis largos (mínimo 64dp de altura). **Os ícones metálicos e os rótulos permanecem fixos e imutáveis**, garantindo a estética original de painel de fábrica:

1. **Slot 1 (Música):** Ícone de nota musical. *Padrão primário:* Cluster Player (`com.joaohouto.clusterplayer`).
2. **Slot 2 (Navegação / GPS):** Ícone de bússola/seta de rota. *Padrão primário:* Google Maps ou Waze.
3. **Slot 3 (Rádio FM):** Ícone de ondas de frequência/antena. *Padrão primário:* App nativo de rádio da multimídia (resolvido automaticamente via lista de pacotes automotivos).
4. **Slot 4 (Telefone):** Ícone de chamada/Bluetooth. *Padrão primário:* Discador Bluetooth da multimídia.
5. **Slot 5 (Ajustes):** Ícone de engrenagem metálica. *Padrão:* Configurações nativas do Android (`android.settings.SETTINGS`).

**Interações da Quick Dock:**
* **Toque Simples:** Abre o pacote associado ao slot. Se o pacote não estiver instalado, abre imediatamente o seletor.
* **Toque Longo (Hold - 500ms):** Abre o modal `SlotAppPickerModal`, permitindo vincular qualquer app da central àquele slot físico.
* **Persistência Instantânea:** A associação é salva no DataStore Preferences via chave `slot_package_{id}`.

---

### 3.2 Página 1: App Drawer (Grade de Aplicativos)

* **Navegação:** Acessada ao deslizar suavemente da direita para a esquerda a partir do Cockpit (`HorizontalPager`).
* **Estrutura:** Grade adaptativa com `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 100.dp))`, com padding seguro e rolagem fluida.
* **Otimização de Renderização:** Uso estrito de `key = { it.packageName }` e `contentType = { "app_grid_item" }` para reciclagem perfeita de células sem jank.
* **Filtragem:** O pacote do próprio Launcher é omitido da lista para evitar abrir a tela inicial dentro de si mesma.
* **Itens:** Ícone com cantos arredondados, rótulo legível em 14sp branco com elipse em títulos longos.
* **Ações:**
  * Toque simples: Abre o aplicativo via `packageManager.getLaunchIntentForPackage(...)`.
  * Toque longo: Exibe menu popup rápido para *"Fixar na Quick Dock"*.

---

## 4. Arquitetura de Software e Comunicação Desacoplada

### 4.1 Diagrama de Arquitetura da MediaSession

```text
+-----------------------------+        MediaSessionManager        +-----------------------------+
|    Cluster Player           | --------------------------------> |        Android System       |
|    (ou Spotify / VLC)       |     (MediaSession ativa com token)|        Media Registry       |
+-----------------------------+                                   +-----------------------------+
                                                                                 |
                                                                          Token / Metadata
                                                                                 v
                                                                  +-----------------------------+
                                                                  |      Cluster Launcher       |
                                                                  | (MediaBridgeListenerService)|
                                                                  +-----------------------------+
                                                                                 |
                                                                         MediaController
                                                                                 v
                                                                  +-----------------------------+
                                                                  |         MediaManager        |
                                                                  | (StateFlow<MediaUiState>)   |
                                                                  +-----------------------------+
                                                                                 |
                                                                        Jetpack Compose
                                                                                 v
                                                                  +-----------------------------+
                                                                  |      UniversalMediaCard     |
                                                                  +-----------------------------+
```

### 4.2 Captura Resiliente de Capa de Álbum
O `MediaManager` deve extrair a arte seguindo a prioridade das chaves do framework Android:
1. `MediaMetadata.METADATA_KEY_ART` (Bitmap)
2. `MediaMetadata.METADATA_KEY_ALBUM_ART` (Bitmap)
3. `MediaMetadata.METADATA_KEY_DISPLAY_ICON` (Bitmap)
4. `MediaMetadata.METADATA_KEY_ALBUM_ART_URI` ou `METADATA_KEY_ART_URI` (String / Uri processado via Coil)
5. Fallback para arte vetorial metálica neutra se nenhuma imagem estiver disponível.

### 4.3 Resolução Inteligente de Pacotes em Multimídias Chinesas / Universais
Centrais multimídia (Allwinner, Rockchip PX5/PX6, TS10/TS18, UIS7862) utilizam pacotes proprietários para Rádio e Telefone. A especificação implementa um resolver inteligente com fallback:

```kotlin
object AutomotivePackageResolver {
    val KNOWN_MUSIC_PACKAGES = listOf(
        "com.joaohouto.clusterplayer",
        "com.spotify.music",
        "com.google.android.music"
    )

    val KNOWN_RADIO_PACKAGES = listOf(
        "com.syu.radio",
        "com.microntek.radio",
        "com.ts.radiostation",
        "com.car.radio",
        "com.yecon.radio",
        "com.autochips.radio"
    )

    val KNOWN_BT_PHONE_PACKAGES = listOf(
        "com.syu.bt",
        "com.microntek.bluetooth",
        "com.ts.bt",
        "com.car.btphone",
        "com.google.android.dialer"
    )

    val KNOWN_GPS_PACKAGES = listOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.here.app.maps"
    )

    fun resolveDefaultPackage(context: Context, slotType: DockSlotType): String? {
        val pm = context.packageManager
        val candidates = when (slotType) {
            DockSlotType.Music -> KNOWN_MUSIC_PACKAGES
            DockSlotType.Radio -> KNOWN_RADIO_PACKAGES
            DockSlotType.Phone -> KNOWN_BT_PHONE_PACKAGES
            DockSlotType.Gps -> KNOWN_GPS_PACKAGES
            DockSlotType.Settings -> listOf("com.android.settings")
        }
        return candidates.firstOrNull { isPackageInstalled(pm, it) }
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
```

---

## 5. Configurações de Sistema, AndroidManifest e Tema Nativo

### 5.1 Declaração como Launcher Padrão do Android (`AndroidManifest.xml`)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Obrigatório para varrer todos os apps no Android 11+ (API 30+) -->
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <!-- Permissões para indicadores de periféricos automotivos -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />

    <application
        android:name=".ClusterLauncherApplication"
        android:label="Cluster Launcher"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:largeHeap="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.ClusterLauncher">

        <activity
            android:name=".MainActivity"
            android:launchMode="singleTask"
            android:clearTaskOnLaunch="true"
            android:stateNotNeeded="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density|keyboardHidden"
            android:exported="true">

            <!-- Identifica a Activity como Launcher / Tela Inicial do Android -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- Serviço de captura de MediaSessions do sistema -->
        <service
            android:name=".media.MediaBridgeListenerService"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <!-- Receptor de detecção de montagem/desmontagem de pendrives USB -->
        <receiver
            android:name=".data.receiver.UsbStateReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MEDIA_MOUNTED" />
                <action android:name="android.intent.action.MEDIA_UNMOUNTED" />
                <action android:name="android.intent.action.MEDIA_EJECT" />
                <data android:scheme="file" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

### 5.2 Tema Nativo Dark Metallic (`res/values/themes.xml`)
Previne qualquer flash branco durante a inicialização (cold start) ou retorno para a Home:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">

    <color name="deep_metallic_background">#FF0B0C0E</color>
    <color name="surface_card">#FF17181B</color>

    <style name="Theme.ClusterLauncher" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/deep_metallic_background</item>
        <item name="android:colorBackground">@color/deep_metallic_background</item>
        <item name="android:statusBarColor">@color/deep_metallic_background</item>
        <item name="android:navigationBarColor">@color/deep_metallic_background</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">false</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">false</item>
        <item name="android:windowSplashScreenBackground" tools:targetApi="s">@color/deep_metallic_background</item>
    </style>
</resources>
```

### 5.3 Navegação e Ergonomia de Launcher (`MainActivity.kt`)
* **Interceptação do Botão Voltar (`BackHandler`):** Se o usuário estiver na Página 1 (App Drawer), pressionar "Voltar" rola suavemente para a Página 0 (Cockpit). Se já estiver no Cockpit, não faz nada (nunca fecha o launcher).
* **Botão Físico "Home" do Volante/Multimídia (`onNewIntent`):** Ao receber uma nova intent de HOME enquanto já aberto, rola automaticamente para o Cockpit (página 0).

---

## 6. Arquitetura Modular do Código-Fonte

```text
com.joaohouto.clusterlauncher/
├── ClusterLauncherApplication.kt     // Configuração global do Coil (RGB_565, limites de cache)
├── MainActivity.kt                   // Configuração da janela, BackHandler e HorizontalPager
├── data/
│   ├── model/
│   │   ├── AppItem.kt                // Nome do app, packageName, ícone (Drawable ou Bitmap)
│   │   ├── DockSlot.kt               // SlotType, ícone vetorial fixo, label fixo, packageName
│   │   └── MediaState.kt             // Título, Artista, Capa, isPlaying, intentAbertura
│   ├── repository/
│   │   ├── AppDrawerRepository.kt    // Leitura assíncrona do PackageManager com LRU Cache
│   │   ├── DockPreferences.kt        // Persistência DataStore para os 5 slots
│   │   └── AutomotivePackageResolver.kt // Resolução de apps automotivos chineses e universais
│   └── receiver/
│       ├── UsbStateReceiver.kt       // BroadcastReceiver de montagem/desmontagem de pendrives
│       └── BluetoothStateReceiver.kt // BroadcastReceiver de conexão Bluetooth
├── media/
│   ├── MediaBridgeListenerService.kt // NotificationListenerService conectado a MediaSessions ativas
│   ├── MediaManager.kt               // Gerencia MediaController e emite StateFlow<MediaState>
│   └── NotificationListenerHelper.kt // Verifica se permissão de acesso a notificações foi concedida
└── ui/
    ├── cockpit/
    │   ├── CockpitScreen.kt          // Layout do Cockpit (Lado Esquerdo + Direito + Base)
    │   ├── components/
    │   │   ├── UniversalMediaCard.kt // Card de mídia desacoplado com fallback amigável
    │   │   ├── ClockWidget.kt        // Relógio digital e data com recomposição isolada
    │   │   ├── SystemStatusRow.kt    // Indicadores offline de USB e Bluetooth
    │   │   └── QuickDockBar.kt       // Barra inferior com os 5 botões de toque largo (mín 64dp)
    │   └── dialogs/
    │       └── SlotAppPickerModal.kt // Diálogo para vincular app ao slot (toque longo)
    ├── drawer/
    │   ├── AppDrawerScreen.kt        // Grade de aplicativos completa em LazyVerticalGrid
    │   ├── AppGridItem.kt            // Célula individual de aplicativo
    │   └── AppDrawerViewModel.kt     // Gerencia lista e estado de busca de aplicativos
    ├── theme/
    │   ├── Color.kt                  // DeepMetallicBackground, NeedleRed, SurfaceCard, etc.
    │   ├── Type.kt                   // Tipografia legível para distância de condução
    │   └── Theme.kt                  // ClusterLauncherTheme (Material3 Dark)
    └── components/
        └── MetallicButton.kt         // Botão chanfrado reutilizável com shapes cacheados
```

---

## 7. Dependências e Otimização R8 (`build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.joaohouto.clusterlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joaohouto.clusterlauncher"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // DataStore Preferences para persistência da Quick Dock
    implementation(libs.androidx.datastore.preferences)

    // Coil para carregamento de capas de mídia e ícones
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
```

---

## 8. Guia de Implementação Passo a Passo

1. **Configuração de Janela e Boot Escuro:**
   * Criar o projeto travado em `sensorLandscape`, `minSdk 24`.
   * Configurar `Theme.ClusterLauncher` nativo com `windowBackground = #0B0C0E` para cold start escuro instantâneo.
   * Configurar `enableEdgeToEdge(SystemBarStyle.dark, SystemBarStyle.dark)`.
2. **Serviço Universal de Mídia (`MediaBridgeListenerService`):**
   * Estender `NotificationListenerService` e registrar no manifesto com permissão `BIND_NOTIFICATION_LISTENER_SERVICE`.
   * Obter instâncias de `MediaController` via `MediaSessionManager.getActiveSessions(...)`.
   * Monitorar mudanças de metadados e estado de reprodução.
   * Criar fallback visual no `UniversalMediaCard` com botão para ativar a permissão no Android caso inativa.
3. **Cockpit Dashboard & Isolamento de Recomposições:**
   * Construir `ClockWidget` isolado (ticker interno) exibindo hora e data com base no RTC do dispositivo.
   * Construir `SystemStatusRow` observando broadcasts de USB e Bluetooth de forma puramente reativa.
   * Construir `UniversalMediaCard` com botões de transporte tácteis largos (mínimo 56x56 dp).
4. **Quick Dock e Resolução Inteligente:**
   * Criar a barra inferior com os 5 botões táteis (Música, GPS, Rádio, Telefone, Ajustes).
   * Implementar resolução automática via `AutomotivePackageResolver` com persistência em DataStore.
   * Implementar `combinedClickable`: toque simples abre o app; toque longo abre `SlotAppPickerModal`.
5. **App Drawer Otimizado:**
   * Varrer `PackageManager` em `Dispatchers.IO` filtrando o próprio pacote.
   * Renderizar com `LazyVerticalGrid`, chaves estáveis (`key = { it.packageName }`) e cache de ícones em memória com `RGB_565`.
6. **Navegação do Launcher:**
   * Configurar `HorizontalPager` alternando suavemente entre Cockpit (Página 0) e Drawer (Página 1).
   * Adicionar `BackHandler` e `onNewIntent` para rolar à Página 0 em vez de fechar a Activity.
7. **Compilação e R8:**
   * Ativar minificação R8 e encolhimento de recursos para gerar um binário leve (~3MB) com inicialização ultrarrápida.