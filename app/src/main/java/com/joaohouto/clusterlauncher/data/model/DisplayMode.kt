package com.joaohouto.clusterlauncher.data.model

/**
 * Modo de contraste/iluminação do Cockpit.
 * Mantém sempre o aspecto escuro automotivo, variando entre:
 * - [AUTO]: segue o farol do carro (broadcasts ILL) ou o modo noturno do sistema Android (uiMode).
 * - [DAY]: alto contraste diurno com cinzas mais claros, bordas nítidas e sem vinheta no mapa.
 * - [NIGHT]: escuro suave para conforto visual em condução noturna.
 */
enum class DisplayMode(val id: String) {
    AUTO("auto"),
    DAY("day"),
    NIGHT("night");

    companion object {
        fun fromId(id: String?): DisplayMode {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: AUTO
        }

        /**
         * Resolve se o app deve exibir o modo Dia (Alto Contraste).
         *
         * Prioridade em modo [AUTO]:
         * 1. [headlightOn] (sinal direto do fio ILL / CANBUS da multimídia, se já recebido):
         *    farol ligado -> Noite (false); farol desligado -> Dia (true).
         * 2. [isSystemNight] (`Configuration.UI_MODE_NIGHT_YES`):
         *    noite do sistema -> Noite (false); dia do sistema -> Dia (true).
         */
        fun resolveIsDayHighContrast(
            mode: DisplayMode,
            isSystemNight: Boolean,
            headlightOn: Boolean? = null
        ): Boolean {
            return when (mode) {
                DAY -> true
                NIGHT -> false
                AUTO -> when (headlightOn) {
                    true -> false
                    false -> true
                    null -> !isSystemNight
                }
            }
        }
    }
}
