package language.util

fun <T> Collection<T>.hasDuplicates() = toHashSet().size != size

inline fun <reified T> Iterable<*>.takeWhileIsInstance(): List<T> {
    val result = mutableListOf<T>()
    for (elem in this) {
        if (elem is T)
            result += elem
        else
            break
    }
    return result
}

@Suppress("unchecked_cast")
inline fun <reified T> Iterable<*>.mapIfAllInstance(): List<T>? {
    val result = mutableListOf<T>()
    for (item in this) {
        if (item is T) result += item
        else return null
    }
    return result
}