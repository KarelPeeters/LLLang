package language.high

open class AbstractParser(val tokenizer: Tokenizer) {
    val currentPosition
        get() = next.position

    var next = tokenizer.next()
        private set
    var lookahead = tokenizer.next()
        private set

    protected fun at(type: TokenType) = next.type == type

    protected fun expect(type: TokenType): Token {
        if (!at(type))
            error("expected $type, got ${next.type} at ${next.position}")
        return pop()
    }

    protected fun expect(vararg types: TokenType) {
        for (type in types) {
            expect(type)
        }
    }

    protected fun accept(type: TokenType) = at(type).also { if (it) pop() }

    protected fun pop() = next.also { pop(1) }

    protected fun pop(count: Int) {
        repeat(count) {
            next = lookahead
            lookahead = tokenizer.next()
        }
    }

    protected fun error(msg: String): Nothing
            = throw ParseError(msg)

    protected fun unexpected(): Nothing = error("Unexpected token $next")
}

class ParseError(message: String) : Exception(message)