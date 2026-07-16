package dev.shusenzheng64.courseaxis.data

data class SyncConfiguration(
    val serverUrl: String = "",
    val token: String = "",
    val revision: Long = 0,
)

interface SyncSettings {
    fun load(): SyncConfiguration

    fun save(url: String, token: String)

    fun setRevision(value: Long)

    fun clear()
}
