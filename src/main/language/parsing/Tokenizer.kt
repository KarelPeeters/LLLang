package language.parsing

data class SourcePosition(
        val line: Int,
        val column: Int
) {
    override fun toString() = "$line:$column"
}

data class Token<T>(
        val type: T,
        val text: String,
        val position: SourcePosition
)

abstract class Tokenizer<T>(private val source: String) {
    private var index = 0
    private var line = 1
    private var column = 1

    abstract fun next(): Token<T>

    protected fun currentPosition() = SourcePosition(line, column)

    protected fun skipCommentsAndWhiteSpace() {
        while (!reachedEOF()) {
            when {
                first()!!.isWhitespace() -> eat()
                accept("//") -> skipPast("\n", eofOk = true)
                accept("/*") -> skipPast("*/", eofOk = false)
                else -> return
            }
        }
    }

    private fun skipPast(str: String, eofOk: Boolean) {
        while (!at(str)) {
            if (reachedEOF()) {
                if (eofOk)
                    return
                else
                    throw TokenizeError("expected '$str', got EOF")
            }
            eat()
        }

        eat(str.length)
    }

    protected fun first(): Char? = source.getOrNull(index)

    protected fun getOrNull(at: Int): Char? = source.getOrNull(index + at)

    protected fun eat(): Char {
        noEOF()
        val char = source[index]
        eat(1)
        return char
    }

    protected fun eat(amount: Int) {
        noEOF(amount)
        repeat(amount) {
            val char = source[index++]
            if (char == '\n') {
                line++
                column = 1
            } else {
                column++
            }
        }
    }

    private fun noEOF(amount: Int = 1) {
        if (index + amount - 1 > source.lastIndex) {
            throw TokenizeError("unexpected EOF")
        }
    }

    protected fun reachedEOF() = index > source.lastIndex

    protected fun at(prefix: String) = source.regionMatches(index, prefix, 0, prefix.length)

    protected fun accept(prefix: String) = at(prefix).also { if (it) eat(prefix.length) }

    protected fun error(message: String): Nothing = throw TokenizeError(message)
}

private class TokenizeError(message: String) : Exception(message)

