package dev.shusenzheng64.courseaxis

enum class AppDestination { WEEK, SETTINGS }

object AppRoute {
    fun parse(value: String?): AppDestination = when (value?.trim()?.lowercase()) {
        "settings" -> AppDestination.SETTINGS
        else -> AppDestination.WEEK
    }
}

data class AppNavigationRequest(
    val destination: AppDestination,
    val sequence: Long = 0,
)
