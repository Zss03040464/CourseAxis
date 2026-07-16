package dev.shusenzheng64.courseaxis.core

/** Keep byte-for-byte aligned with macOS StableID. */
object StableId {
    private const val OFFSET: ULong = 0xcbf29ce484222325uL
    private const val PRIME: ULong = 0x100000001b3uL
    fun of(prefix: String, vararg parts: Any?): String {
        val input = parts.joinToString("\u001F") { it?.toString()?.trim().orEmpty() }
        var hash = OFFSET
        for (byte in input.toByteArray(Charsets.UTF_8)) { hash = hash xor byte.toUByte().toULong(); hash *= PRIME }
        return "$prefix-${hash.toString(16)}"
    }
    fun colorKey(value: String, size: Int = 12): Int {
        require(size > 0); var hash = OFFSET
        for (byte in value.toByteArray(Charsets.UTF_8)) { hash = hash xor byte.toUByte().toULong(); hash *= PRIME }
        return (hash % size.toULong()).toInt()
    }
}
