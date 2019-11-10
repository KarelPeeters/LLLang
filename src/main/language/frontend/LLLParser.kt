package language.frontend

import language.frontend.LLLTokenType.*
import language.frontend.LLLTokenType.Number
import language.frontend.LLLTokenType.Struct
import language.ir.ArithmeticOpType
import language.ir.BinaryOpType
import language.ir.ComparisonOpType
import language.parsing.Parser
import language.parsing.SourcePosition
import language.frontend.Struct as ASTStruct

class LLLParser(tokenizer: LLLTokenizer) : Parser<LLLTokenType>(tokenizer) {
    companion object {
        fun parse(input: String) = LLLParser(LLLTokenizer(input)).parse()
    }

    fun parse() = program()

    private fun program() = Program(currentPosition, sequence {
        while (!at(Eof)) {
            yield(topLevel())
        }
    }.toList())

    private fun topLevel(): TopLevel {
        return when {
            at(Struct) -> struct()
            at(Fun) -> function()
            else -> expected("top level declaration")
        }
    }

    private fun struct(): ASTStruct {
        val pos = expect(Struct).position
        val name = expect(Id).text
        val properties = if (accept(OpenB)) list(CloseB, ::parameter) else emptyList()
        val functions = if (accept(OpenC)) list(CloseC, null, ::function) else emptyList()

        return ASTStruct(pos, name, properties, functions)
    }

    private fun function(): Function {
        val pos = expect(Fun).position
        val name = expect(Id).text
        expect(OpenB)
        val parameters = list(CloseB, ::parameter)
        val ret = if (accept(Colon)) type() else null

        val content = when {
            at(OpenC) -> Function.FunctionBody.Block(pop().position, block(CloseC))
            at(Assign) -> Function.FunctionBody.Expr(pop().position, expression()).also { expect(Semi) }
            else -> expected("function body")
        }

        return Function(pos, name, parameters, ret, content)
    }

    private fun parameter(): Parameter {
        val id = expect(Id)
        expect(Colon)
        return Parameter(id.position, id.text, type())
    }

    private fun block(end: LLLTokenType) = CodeBlock(currentPosition, sequence {
        while (!accept(end)) {
            if (accept(Semi))
                continue
            yield(statement())
        }
    }.toList())

    private fun containedBlock() = if (accept(OpenC))
        block(CloseC)
    else
        CodeBlock(currentPosition, listOf(statement()))

    private fun statement(): Statement {
        return when {
            at(If) -> return ifStatement()
            at(While) -> return whileStatement()
            at(Break) -> BreakStatement(pop().position)
            at(Continue) -> ContinueStatement(pop().position)
            at(Return) -> returnStatement()
            at(Var) || at(Val) -> declaration()
            at(OpenC) -> return containedBlock()
            else -> expression()
        }.also { expect(Semi) }
    }

    private fun declaration(): Statement {
        val pos = currentPosition
        val mutable = when {
            accept(Var) -> true
            accept(Val) -> false
            else -> expected("variable declaration")
        }
        val identifier = expect(Id).text
        val type = if (accept(Colon)) type() else null
        val value = if (accept(Assign)) expression() else null

        return Declaration(pos, identifier, mutable, type, value)
    }

    private fun returnStatement(): Statement {
        val pos = expect(Return).position
        return when {
            at(Semi) -> ReturnStatement(pos, null)
            else -> ReturnStatement(pos, expression())
        }
    }

    private fun ifStatement(): Statement {
        val pos = currentPosition
        expect(If, OpenB)
        val condition = expression()
        expect(CloseB)
        val thenBlock = containedBlock()
        val elseBlock = if (accept(Else)) containedBlock() else null
        return IfStatement(pos, condition, thenBlock, elseBlock)
    }

    private fun whileStatement(): Statement {
        val pos = currentPosition
        expect(While, OpenB)
        val condition = expression()
        expect(CloseB)
        val block = containedBlock()
        return WhileStatement(pos, condition, block)
    }

    private fun expressionList(end: LLLTokenType) = list(end) { expression() }

    private inline fun <E> list(end: LLLTokenType, element: () -> E) = list(end, Comma, element)

    private fun expression(): Expression = precedenceClimb(-1)

    private fun precedenceClimb(lowerLevel: Int): Expression {
        var curr = unary()

        while (true) {
            val info = operatorInfo[next.type]
            if (info == null || info.level < lowerLevel) break

            val pos = pop().position

            val level = if (info.bindLeft) info.level + 1 else info.level
            val next = precedenceClimb(level)

            curr = info.build(pos, curr, next)
        }

        return curr
    }

    private fun unary(): Expression {
        val preList = mutableListOf<Pair<SourcePosition, UnaryOpType>>()
        loop@ while (true) {
            preList += currentPosition to when {
                accept(Bang) -> UnaryOpType.Not
                accept(Minus) -> UnaryOpType.Minus
                accept(Plus) -> UnaryOpType.Plus
                accept(Inc) -> UnaryOpType.PreInc
                accept(Dec) -> UnaryOpType.PreDec
                else -> break@loop
            }
        }

        var curr = atomic()

        loop@ while (true) {
            val pos = currentPosition
            curr = when {
                accept(Inc) -> UnaryOp(pos, UnaryOpType.PostInc, curr)
                accept(Dec) -> UnaryOp(pos, UnaryOpType.PostDec, curr)
                accept(OpenB) -> Call(pos, curr, expressionList(CloseB))
                accept(OpenS) -> ArrayIndex(pos, curr, expression()).also { expect(CloseS) }
                accept(Dot) -> DotIndex(currentPosition, curr, expect(Id).text)
                else -> break@loop
            }
        }

        return preList.foldRight(curr) { (p, t), a -> UnaryOp(p, t, a) }
    }

    private fun atomic(): Expression = when {
        accept(OpenB) -> expression().also { expect(CloseB) }
        at(OpenC) -> arrayInitializer()
        at(True) -> BooleanLiteral(pop().position, true)
        at(False) -> BooleanLiteral(pop().position, false)
        at(Number) -> NumberLiteral(currentPosition, pop().text)
        at(Id) -> IdentifierExpression(currentPosition, pop().text)
        at(This) -> ThisExpression(pop().position)
        else -> expected("atomic expression")
    }

    private fun arrayInitializer(): Expression {
        val pos = expect(OpenC).position
        val values = expressionList(CloseC)
        return ArrayInitializer(pos, values)
    }

    private fun type(): TypeAnnotation = when {
        //identifier
        at(Id) -> TypeAnnotation.Simple(currentPosition, pop().text)
        //function
        at(OpenB) -> {
            val pos = pop().position
            val params = list(CloseB) { type() }
            expect(Arrow)
            val returnType = type()
            TypeAnnotation.Function(pos, params, returnType)
        }
        //array
        at(OpenS) -> {
            val pos = pop().position
            val innerType = type()
            expect(Comma)
            val size = expect(Number).text.toInt()
            expect(CloseS)

            TypeAnnotation.Array(pos, innerType, size)
        }
        else -> expected("type")
    }
}

data class BinaryOpInfo(
        val level: Int,
        val bindLeft: Boolean,
        val build: (SourcePosition, Expression, Expression) -> Expression
) {
    constructor(level: Int, bindLeft: Boolean, type: BinaryOpType) :
            this(level, bindLeft, { p, l, r -> BinaryOp(p, type, l, r) })
}

val operatorInfo = mapOf(
        Assign to BinaryOpInfo(0, false, ::Assignment),

        Pipe to BinaryOpInfo(1, true, ArithmeticOpType.Or),
        DoublePipe to BinaryOpInfo(1, true, ArithmeticOpType.Or),

        Amper to BinaryOpInfo(2, true, ArithmeticOpType.And),
        DoubleAmper to BinaryOpInfo(2, true, ArithmeticOpType.And),

        EQ to BinaryOpInfo(3, true, ComparisonOpType.EQ),
        NEQ to BinaryOpInfo(3, true, ComparisonOpType.NEQ),

        LT to BinaryOpInfo(4, true, ComparisonOpType.LT),
        GT to BinaryOpInfo(4, true, ComparisonOpType.GT),
        LTE to BinaryOpInfo(4, true, ComparisonOpType.LTE),
        GTE to BinaryOpInfo(4, true, ComparisonOpType.GTE),

        Plus to BinaryOpInfo(5, true, ArithmeticOpType.Add),
        Minus to BinaryOpInfo(5, true, ArithmeticOpType.Sub),

        Times to BinaryOpInfo(6, true, ArithmeticOpType.Mul),
        Divide to BinaryOpInfo(6, true, ArithmeticOpType.Div),
        Percent to BinaryOpInfo(6, true, ArithmeticOpType.Mod)
)
