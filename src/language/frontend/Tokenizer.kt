package language.frontend

data class SourcePosition(
        val line: Int,
        val column: Int
) {
    override fun toString() = "$line:$column"
}

enum class TokenType(val string: String? = null) {
    //brackets
    OpenB("("),
    CloseB(")"),
    OpenS("["), CloseS("]"),
    OpenC("{"), CloseC("}"),

    //symbols
    Inc("++"),
    Dec("--"),
    Power("**"),
    Plus("+"), Minus("-"), Times("*"), Divide("/"), Percent("%"),
    DoubleAmper("&&"), DoublePipe("||"),
    Amper("&"), Pipe("|"), Caret("^"),
    LTE("<="), GTE(">="),
    LT("<"), GT(">"), EQ("=="), NEQ("!="),
    Bang("!"), Tilde("~"),
    Assign("="), Semi(";"), EndLn("\n"), Colon(":"), Comma(","),

    //keywords
    If("if"),
    Else("else"),
    While("while"), For("for"),
    Break("break"), Continue("continue"),
    Fun("fun"),
    Val("val"), Var("var"),

    //values
    Boolean(),
    Number(),
    Id(),

    //elseBlock
    Eof(),
    ;
}

data class Token(
        val type: TokenType,
        val text: String,
        val position: SourcePosition
)

class TokenizeError(message: String) : Exception(message)

class Tokenizer(private val source: String) {
    private var index = 0
    private var line = 1
    private var column = 1

    fun next(): Token {
        skipCommentsAndWhiteSpace()
        if (reachedEOF())
            return Token(TokenType.Eof, "", currentPosition())

        val position = currentPosition()

        //trivial match
        for (type in TokenType.values()) {
            val str = type.string ?: continue
            if (startsWith(str)) {
                eat(str.length)
                return Token(type, str, position)
            }
        }

        //boolean
        when {
            startsWithEat("true") -> return Token(TokenType.Boolean, "true", position)
            startsWithEat("false") -> return Token(TokenType.Boolean, "false", position)
        }

        //number
        if (first() in '0'..'9') {
            var string = eat().toString()
            while (first() in '0'..'9')
                string += eat()
            return Token(TokenType.Number, string, position)
        }

        //identifier
        if (first() in ID_START_CHARS) {
            var string = eat().toString()
            while (first() in ID_CHARS)
                string += eat()
            return Token(TokenType.Id, string, position)
        }

        throw TokenizeError("unexpected character '${first()}'")
    }


    private fun currentPosition() = SourcePosition(line, column)

    private fun skipCommentsAndWhiteSpace() {
        while (!reachedEOF()) {
            when {
                first()!!.isWhitespace() -> eat()
                startsWith("//") -> skipPast("\n")
                startsWith("/*") -> skipPast("*/")
                else -> return
            }
        }
    }

    private fun skipPast(str: String) {
        while (!startsWith(str))
            eat()
        eat(str.length)
    }

    private fun first(): Char? {
        return source.getOrNull(index)
    }

    private fun eat(): Char {
        noEOF()
        val char = source[index]
        eat(1)
        return char
    }

    private fun eat(amount: Int) {
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

    private fun reachedEOF() = index > source.lastIndex

    private fun startsWith(prefix: String) = source.regionMatches(index, prefix, 0, prefix.length)

    private fun startsWithEat(prefix: String) = startsWith(prefix).also {
        if (it) eat(prefix.length)
    }
}

private val ID_START_CHARS = (('a'..'z') + ('A'..'Z') + '_' + '-').toSet()

private val ID_CHARS = ID_START_CHARS + ('0'..'9')