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

fun <T, F, R> Iterable<T>.mapFold(initial: F, block: (F, T) -> Pair<F, R>): Pair<F, List<R>> {
    var acc = initial
    val list = this.map {
        val (na, nt) = block(acc, it)
        acc = na
        nt
    }
    return acc to list
}

fun <T> Collection<T>.hasDuplicates() = toHashSet().size != size