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