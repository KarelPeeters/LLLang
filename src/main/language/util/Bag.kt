package language.util

class Bag<K> {
    val map: MutableMap<K, Int>

    constructor() {
        map = mutableMapOf()
    }

    constructor(map: Map<K, Int>) {
        this.map = map.toMutableMap()
    }

    val size get() = map.size

    val keys get(): Set<K> = map.keys

    fun get(key: K): Int = map[key] ?: 0

    fun add(key: K, delta: Int = 1): Int {
        val new = (map[key] ?: 0) + delta

        when {
            new < 0 -> throw IllegalStateException("$key went below 0")
            new > 0 -> map[key] = new
            else -> map.remove(key)
        }

        return new
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Bag<*>
        return map == other.map
    }

    override fun hashCode(): Int = map.hashCode()
}

fun <T> Map<T, Int>.toBag() = Bag(this)