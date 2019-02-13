package language.util

fun <T> MutableList<T>.replace(from: T, to: T): Boolean {
    val iter = listIterator()
    var replaced = false
    for (e in iter) {
        if (e == from) {
            iter.set(to)
            replaced = true
        }
    }
    return replaced
}

fun <K, V> MutableMap<K, V>.replaceValues(from: V, to: V): Boolean {
    val iter = iterator()
    var replaced = false
    for (entry in iter) {
        if (entry.value == from) {
            entry.setValue(to)
            replaced = true
        }
    }
    return replaced
}