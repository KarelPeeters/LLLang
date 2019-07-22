package language.frontend

import language.parsing.Token
import language.parsing.Tokenizer

enum class LLLTokenType(val string: String? = null) {
    OpenB("("), CloseB(")"),
    OpenS("["), CloseS("]"),
    OpenC("{"), CloseC("}"),

    Arrow("->"), Inc("++"), Dec("--"), Power("**"),
    Plus("+"), Minus("-"), Times("*"), Divide("/"), Percent("%"),
    DoubleAmper("&&"), DoublePipe("||"),
    Amper("&"), Pipe("|"), Caret("^"),
    LTE("<="), GTE(">="),
    LT("<"), GT(">"), EQ("=="), NEQ("!="),
    Bang("!"), Tilde("~"),
    Assign("="), Semi(";"),
    Colon(":"), Comma(","), Dot("."),

    Struct("struct"), This("this"),
    If("if"), Else("else"),
    While("while"), For("for"),
    Break("break"), Continue("continue"),
    Fun("fun"), Return("return"),
    Val("val"), Var("var"),

    True("true"), False("false"),
    Number,
    Id,

    Eof;

    val startOfIdentifier = string?.let { str ->
        str[0] in ID_START_CHARS && str.substring(1).all { it in ID_CHARS }
    } ?: false
}

class LLLTokenizer(source: String) : Tokenizer<LLLTokenType>(source) {
    override fun next(): Token<LLLTokenType> {
        skipCommentsAndWhiteSpace()
        if (reachedEOF())
            return Token(LLLTokenType.Eof, "", currentPosition())

        val position = currentPosition()

        //trivial match
        for (type in LLLTokenType.values()) {
            val str = type.string ?: continue

            if (at(str)) {
                if (type.startOfIdentifier && getOrNull(str.length) in ID_CHARS)
                    continue

                eat(str.length)
                return Token(type, str, position)
            }
        }

        //number
        if (first() in '0'..'9') {
            val builder = StringBuilder()
            builder.append(eat())
            while (first() in '0'..'9')
                builder.append(eat())
            return Token(LLLTokenType.Number, builder.toString(), position)
        }

        //identifier
        if (first() in ID_START_CHARS) {
            val builder = StringBuilder()
            builder.append(eat())
            while (first() in ID_CHARS)
                builder.append(eat())
            return Token(LLLTokenType.Id, builder.toString(), position)
        }

        error("unexpected character '${first()}'")
    }
}

private val ID_START_CHARS = (('a'..'z') + ('A'..'Z') + '_').toSet()
private val ID_CHARS = ID_START_CHARS + ('0'..'9') + '-'