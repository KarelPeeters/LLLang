package language.frontend

import language.frontend.TokenType.*
import language.frontend.TokenType.Boolean
import language.frontend.TokenType.Number
import language.ir.ArithmeticOpType
import language.ir.ComparisonOpType
import java.util.*
import kotlin.coroutines.experimental.buildSequence

class LLLParser(tokenizer: Tokenizer) : AbstractParser(tokenizer) {
    fun parse() = block(Eof)

    private fun block(end: TokenType) = CodeBlock(currentPosition, buildSequence {
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
            at(Var) -> declaration()
            at(OpenC) -> return containedBlock()
            else -> expression()
        }.also { expect(Semi) }
    }

    private fun declaration(): Statement {
        val pos = currentPosition
        expect(Var)
        val identifier = expect(Id).text
        val type = if (accept(Colon)) type() else null
        expect(Assign)
        val value = expression()

        return Declaration(pos, identifier, type, value)
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

    private fun expressionList(end: TokenType): List<Expression> {
        val list = mutableListOf<Expression>()
        if (!accept(end)) {
            list += expression()

            while (!accept(end)) {
                expect(Comma)
                list += expression()
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
        val ops = LinkedList<Pair<UnaryOp.Type, SourcePosition>>()
        loop@ while (true) {
            val pos = currentPosition
            val type = when {
                /*accept(Inc) -> UnaryOp.Type.PreInc
                accept(Dec) -> UnaryOp.Type.PreDec*/
                accept(Plus) -> UnaryOp.Type.Positive
                accept(Minus) -> UnaryOp.Type.Negative
                accept(Bang) -> UnaryOp.Type.BNot
                accept(Tilde) -> UnaryOp.Type.INot
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
                /*accept(Inc) -> UnaryOp(pos, UnaryOp.Type.PostInc, expr)
                accept(Dec) -> UnaryOp(pos, UnaryOp.Type.PostDec, expr)*/
                accept(OpenB) -> Call(pos, expr, expressionList(CloseB))
                accept(OpenS) -> Index(pos, expr, expression())
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

    private fun type() = TypeAnnotation(currentPosition, expect(Id).text)
}