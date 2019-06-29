package language.ir.support

import language.ir.BINARY_OP_TYPES
import language.ir.UNARY_OP_TYPES
import language.parsing.Token
import language.parsing.Tokenizer

enum class IrTokenType(val string: String? = null) {
    OpenB("("), CloseB(")"),
    OpenS("["), CloseS("]"),
    OpenC("{"), CloseC("}"),

    Arrow("->"),
    Assign("="),
    Comma(","),
    Colon(":"),
    Star("*"),

    Fun("fun"),
    Entry("entry"),
    TypeDef("type"),

    AllocToken("alloc"),
    StoreToken("store"), LoadToken("load"),
    BinaryOpToken, UnaryOpToken,
    PhiToken("phi"),
    EatToken("eat"), BlurToken("blur"),
    CallToken("call"),
    StructGetToken("sget"), ArrayGetToken("aget"),
    StructPtrToken("sptr"), ArrayPtrToken("aptr"),
    BranchToken("branch"), JumpToken("jump"),
    ExitToken("exit"), ReturnToken("return"),

    UnitTypeToken("Unit"), UnitValueToken("unit"),
    IntegerTypeToken,

    Number,
    Id,
    BlockId,

    EndLn("\n"),
    Eof;
}

class IrTokenizer(source: String) : Tokenizer<IrTokenType>(source) {
    override fun next(): Token<IrTokenType> {
        skipCommentsAndWhiteSpace()
        if (reachedEOF())
            return Token(IrTokenType.Eof, "", currentPosition())

        val position = currentPosition()

        //trivial match
        for (type in IrTokenType.values()) {
            val str = type.string ?: continue
            if (accept(str))
                return Token(type, str, position)
        }

        //number
        if (first() in '0'..'9') {
            val string = expectNumber()
            return Token(IrTokenType.Number, string, position)
        }

        //identifier
        if (first() == '%') {
            val builder = StringBuilder()
            builder.append(eat())
            while (first() in ID_CHARS)
                builder.append(eat())

            return Token(IrTokenType.Id, builder.toString(), position)
        }

        if (first() == '<') {
            val builder = StringBuilder()
            builder.append(eat())
            while (first() != '>')
                builder.append(eat())
            builder.append(eat())

            return Token(IrTokenType.BlockId, builder.toString(), position)
        }

        //binaryop and unaryop
        for (type in BINARY_OP_TYPES) {
            if (accept(type.name))
                return Token(IrTokenType.BinaryOpToken, type.name, position)
        }

        for (type in UNARY_OP_TYPES) {
            if (accept(type.name))
                return Token(IrTokenType.UnaryOpToken, type.name, position)
        }

        //integertype
        if (accept("i")) {
            val string = "i" + expectNumber()
            return Token(IrTokenType.IntegerTypeToken, string, position)
        }

        error("unexpected character '${first()}' at $position")
    }

    private fun expectNumber(): String {
        if (first() !in '0'..'9')
            error("Expected digit, got '${first()}' at ${currentPosition()}")

        val builder = StringBuilder()
        builder.append(eat())
        while (first() in '0'..'9')
            builder.append(eat())
        return builder.toString()
    }
}

private val ID_CHARS = (('a'..'z') + ('A'..'Z') + ('0'..'9') + '_' + '-').toSet()