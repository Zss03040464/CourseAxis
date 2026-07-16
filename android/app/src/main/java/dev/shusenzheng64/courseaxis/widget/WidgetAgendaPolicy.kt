package dev.shusenzheng64.courseaxis.widget

object WidgetAgendaPolicy {
    fun capacity(widthDp: Int, heightDp: Int, requested: Int): Int {
        val available = if (widthDp < 160) {
            1
        } else {
            ((heightDp - 45) / 38).coerceIn(1, 5)
        }
        return requested.coerceIn(1, 5).coerceAtMost(available)
    }
}
