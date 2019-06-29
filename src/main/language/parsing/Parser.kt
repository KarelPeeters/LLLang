package language.parsing

/**
 * Base class for frontend parsers. Contains functions for consuming tokens generated by the passed tokenizer.
 */
abstract class Parser<T>(private val tokenizer: Tokenizer<T>) {
    val currentPosition
        get() = next.position

    var next = tokenizer.next()
        private set
    var lookahead = tokenizer.next()
        private set

    protected fun at(type: T) = next.type == type

    protected fun expect(type: T): Token<T> {
        if (!at(type))
            error("expected $type, got ${next.type} at ${next.position}")
        return pop()
    }

    protected fun expect(vararg types: T) {
        for (type in types) {
            expect(type)
        }
    }

    protected fun accept(type: T) = at(type).also { if (it) pop() }

    protected fun pop() = next.also {
        next = lookahead
        lookahead = tokenizer.next()
    }

    protected fun unexpected(): Nothing = error("Unexpected token $next")

    protected fun error(msg: String): Nothing = throw ParseError(msg)

    protected inline fun <E> list(end: T, separator: T?, element: () -> E): List<E> {
        val list = mutableListOf<E>()
        while (!accept(end)) {
            if (separator != null && list.isNotEmpty()) {
                expect(separator)
                if (accept(end)) break //allow trailing separator
            }

            list += element()
        }
        return list
    }
}

private class ParseError(message: String) : Exception(message)