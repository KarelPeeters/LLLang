package language.frontend

import language.frontend.TokenType.*
import language.frontend.TokenType.Boolean
import language.frontend.TokenType.Number
import language.frontend.TokenType.Struct
import language.ir.ArithmeticOpType
import language.ir.ComparisonOpType
import language.ir.UnaryOpType
import java.util.*
import language.frontend.Struct as ASTStruct

class LLLParser(tokenizer: Tokenizer) : AbstractParser(tokenizer) {
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
            else -> unexpected()
        }
    }

    private fun struct(): ASTStruct {
        val pos = currentPosition
        expect(Struct)
        val name = expect(Id).text
        expect(OpenB)
        val properties = list(CloseB, ::parameter)
        return ASTStruct(pos, name, properties)
    }

    private fun function(): Function {
        val pos = currentPosition
        expect(Fun)
        val name = expect(Id).text
        expect(OpenB)
        val parameters = list(CloseB, ::parameter)
        val ret = if (accept(Colon)) type() else null

        val content = when {
            accept(OpenC) -> Function.FunctionBody.Block(block(CloseC))
            accept(Assign) -> Function.FunctionBody.Expr(expression())
            else -> unexpected()
        }

        return Function(pos, name, parameters, ret, content)
    }

    private fun parameter(): Parameter {
        val pPos = currentPosition
        val pName = expect(Id).text
        expect(Colon)
        return Parameter(pPos, pName, type())
    }

    private fun block(end: TokenType) = CodeBlock(currentPosition, sequence {
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
            accept(Break) -> BreakStatement(currentPosition)
            accept(Continue) -> ContinueStatement(currentPosition)
            accept(Return) -> returnStatement()
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
            else -> unexpected()
        }
        val identifier = expect(Id).text
        val type = if (accept(Colon)) type() else null
        expect(Assign)
        val value = expression()

        return Declaration(pos, identifier, mutable, type, value)
    }

    private fun returnStatement(): Statement = when {
        at(Semi) -> ReturnStatement(currentPosition, null)
        else -> ReturnStatement(currentPosition, expression())
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

    private fun expressionList(end: TokenType) = list(end) { expression() }

    private inline fun <T> list(end: TokenType, element: () -> T): List<T> {
        val list = mutableListOf<T>()
        if (!accept(end)) {
            list += element()

            while (!accept(end)) {
                expect(Comma)
                list += element()
            }
        }
        return list
    }

    private fun expression() = assignment()

    private fun assignment(): Expression {
        val expressions = mutableListOf<Expression>()
        val assignPositions = mutableListOf<SourcePosition>()
        loop@ while (true) {
            expressions += disjunction()
            val assignPos = currentPosition
            if (accept(Assign)) assignPositions += assignPos
            else break@loop
        }

        return expressions.reduceRightIndexed { i, target, value ->
            Assignment(assignPositions[i], target, value)
        }
    }

    private fun disjunction(): Expression {
        var left = conjunction()
        while (true) {
            val pos = currentPosition
            if (!accept(DoublePipe) && !accept(Pipe))
                return left
            left = BinaryOp(pos, ArithmeticOpType.Or, left, conjunction())
        }

    }

    private fun conjunction(): Expression {
        var left = equality()
        val pos = currentPosition
        while (true) {
            if (!accept(DoubleAmper) && !accept(Amper))
                return left
            left = BinaryOp(pos, ArithmeticOpType.And, left, equality())
        }
    }

    private fun equality(): Expression {
        var left = comparison()
        while (true) {
            val pos = currentPosition
            val type = when {
                accept(EQ) -> ComparisonOpType.EQ
                accept(NEQ) -> ComparisonOpType.NEQ
                else -> return left
            }
            left = BinaryOp(pos, type, left, comparison())
        }
    }

    private fun comparison(): Expression {
        var left = addition()
        while (true) {
            val pos = currentPosition
            val type = when {
                accept(LT) -> ComparisonOpType.LT
                accept(GT) -> ComparisonOpType.GT
                accept(LTE) -> ComparisonOpType.LTE
                accept(GTE) -> ComparisonOpType.GTE
                else -> return left
            }
            left = BinaryOp(pos, type, left, addition())
        }
    }

    private fun addition(): Expression {
        var left = multiplication()
        while (true) {
            val pos = currentPosition
            val type = when {
                accept(Plus) -> ArithmeticOpType.Add
                accept(Minus) -> ArithmeticOpType.Sub
                else -> return left
            }
            left = BinaryOp(pos, type, left, multiplication())
        }
    }

    private fun multiplication(): Expression {
        var left = power()
        while (true) {
            val pos = currentPosition
            val type = when {
                accept(Times) -> ArithmeticOpType.Mul
                accept(Divide) -> ArithmeticOpType.Div
                accept(Percent) -> ArithmeticOpType.Mod
                else -> return left
            }
            left = BinaryOp(pos, type, left, power())
        }
    }

    private fun power(): Expression {
        /*var left = prefix()
        while (true) {
            val pos = currentPosition
            if (accept(Power))
                left = BinaryOp(pos, BinaryOpType.Power, left, prefix())
            else
                return left
        }*/
        return prefix()
    }

    private fun prefix(): Expression {
        val ops = ArrayDeque<Pair<UnaryOpType, SourcePosition>>()
        loop@ while (true) {
            val pos = currentPosition
            val type = when {
                accept(Inc) -> TODO()
                accept(Dec) -> TODO()
                accept(Plus) -> TODO()
                accept(Minus) -> UnaryOpType.Neg
                accept(Bang) -> UnaryOpType.Not
                accept(Tilde) -> UnaryOpType.Not
                else -> break@loop
            }
            ops.push(type to pos)
        }
        return ops.fold(suffix()) { acc, (type, pos) -> UnaryOp(pos, type, acc) }
    }

    private fun suffix(): Expression {
        var expr = atomic()
        while (true) {
            val pos = currentPosition
            expr = when {
                accept(Inc) -> TODO()
                accept(Dec) -> TODO()
                accept(OpenB) -> Call(pos, expr, expressionList(CloseB))
                accept(OpenS) -> ArrayIndex(pos, expr, expression())
                accept(Dot) -> DotIndex(currentPosition, expr, expect(Id).text)
                else -> return expr
            }
        }
    }

    private fun atomic(): Expression {
        return when {
            accept(OpenB) -> expression().also { expect(CloseB) }
            at(Boolean) -> BooleanLiteral(currentPosition, pop().text.toBoolean())
            at(Number) -> NumberLiteral(currentPosition, pop().text)
            at(Id) -> IdentifierExpression(currentPosition, pop().text)
            else -> unexpected()
        }
    }

    private fun type(): TypeAnnotation = when {
        at(Id) -> TypeAnnotation.Simple(currentPosition, pop().text)
        at(OpenB) -> {
            val pos = currentPosition
            pop()
            val params = list(CloseB) { type() }
            expect(Arrow)
            val returnType = type()
            TypeAnnotation.Function(pos, params, returnType)
        }
        else -> unexpected()
    }
}