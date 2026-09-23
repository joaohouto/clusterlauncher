# TODOs & Padronização: Cluster Launcher

Este documento lista melhorias de harmonização arquitetural e técnica com o ecossistema **Cluster** (Cluster Player e Cluster Radio).

---

## 1. Internacionalização (i18n)

* **Problema Atual:** O arquivo `app/src/main/res/values/strings.xml` contém diretamente as strings em português (idioma padrão do projeto).
* **Solução Necessária:**
  1. Mover todas as strings em português para um novo diretório localizado: `app/src/main/res/values-pt/strings.xml`.
  2. Traduzir e manter as strings padrão em inglês no arquivo base: `app/src/main/res/values/strings.xml`.
  3. Isso garantirá compatibilidade com centrais multimídia configuradas em outros idiomas (inglês, espanhol, etc.) sem exibir textos fixos em português.

---

## 2. Harmonização de Temas de Cockpit

* Assegurar que os mesmos 7 temas de iluminação de instrumentos (*Cluster Accent Themes*) estejam disponíveis em paridade com ClusterPlayer e ClusterRadio:
  - `needle_red` (`#E61924`)
  - `m_sport_blue` (`#0088FF`)
  - `racing_yellow` (`#FFCC00`)
  - `green_hell` (`#00E676`)
  - `sunset_orange` (`#FFFF6D00`)
  - `electric_cyan` (`#00E5FF`)
  - `pure_silver` (`#E2E8F0`)
