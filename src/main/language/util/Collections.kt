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

/**
 * Returns a sublist up to and _exlusing_ [until]. Throws [NoSuchElementException] if [until] is not found in the list.
 */
fun <T> List<T>.subListUntil(until: T): List<T> {
    val untilIndex = indexOf(until)
    if (untilIndex == -1) throw NoSuchElementException("Element not found")
    return subList(0, untilIndex)
}