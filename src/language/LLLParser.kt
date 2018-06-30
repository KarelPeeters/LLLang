package language

import language.TokenType.*
import language.TokenType.Boolean
import language.TokenType.Number
import java.util.*
import kotlin.coroutines.experimental.buildSequence

class LLLParser(tokenizer: Tokenizer) : AbstractParser(tokenizer) {
    fun parse() = block(Eof)

    private fun block(end: TokenType) = Block(currentPosition, buildSequence {
        while (!accept(end))
            yield(statement())
    }.toList())

    private fun containedBlock() = if (accept(OpenC))
        block(CloseC)
    else
        Block(currentPosition, listOf(statement()))

    private fun statement(): Statement {
        return when {
            at(If) -> return ifStatement()
            at(While) -> return whileStatement()
            accept(Break) -> BreakStatement(currentPosition)
            accept(Continue) -> ContinueStatement(currentPosition)
            at(Var) -> declaration()
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

    private fun expressionList(end: TokenType) = ExpressionList(currentPosition, buildSequence {
        if (!accept(end)) {
            yield(expression())
            while (!accept(end))
                yield(expression())
        }
    }.toList())

    private fun expression() = assignment()

    private fun assignment(): Expression {
        val left = disjunction()
        val pos = currentPosition
        return when {
            accept(Assign) -> Assignment(pos, left, disjunction())
            else -> left
        }
    }

    private fun disjunction(): Expression {
        var left = conjunction()
        while (true) {
            val pos = currentPosition
            val type = when {
                accept(DoublePipe) -> BinaryOp.Type.BOr
                accept(Pipe) -> BinaryOp.Type.Ior
                else -> return left
            }
            left = BinaryOp(pos, type, left, conjunction())
        }

    }

    private fun conjunction(): Expression {
        var left = equality()
        val pos = currentPosition
        while (true) {
            val type = when {
                accept(DoubleAmper) -> BinaryOp.Type.BAnd
                accept(Amper) -> BinaryOp.Type.Iand
                else -> return left
            }
            left = BinaryOp(pos, type, left, equality())
        }
    }

    private fun equality(): Expression {
        var left = comparison()
        while (true) {
            val pos = currentPosition
            val type = when {
                accept(EQ) -> BinaryOp.Type.EQ
                accept(NEQ) -> BinaryOp.Type.NEQ
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
                accept(LT) -> BinaryOp.Type.LT
                accept(GT) -> BinaryOp.Type.GT
                accept(LTE) -> BinaryOp.Type.LTE
                accept(GTE) -> BinaryOp.Type.GTE
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
                accept(Plus) -> BinaryOp.Type.Add
                accept(Minus) -> BinaryOp.Type.Subtract
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
                accept(Times) -> BinaryOp.Type.Multiply
                accept(Divide) -> BinaryOp.Type.Divide
                accept(Percent) -> BinaryOp.Type.Modulus
                else -> return left
            }
            left = BinaryOp(pos, type, left, power())
        }
    }

    private fun power(): Expression {
        var left = prefix()
        while (true) {
            val pos = currentPosition
            if (accept(Power))
                left = BinaryOp(pos, BinaryOp.Type.Power, left, prefix())
            else
                return left
        }
    }

    private fun prefix(): Expression {
        val ops = LinkedList<Pair<UnaryOp.Type, SourcePosition>>()
        loop@ while (true) {
            val pos = currentPosition
            val type = when {
                accept(Inc) -> UnaryOp.Type.PreInc
                accept(Dec) -> UnaryOp.Type.PreDec
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
                accept(Inc) -> UnaryOp(pos, UnaryOp.Type.PostInc, expr)
                accept(Dec) -> UnaryOp(pos, UnaryOp.Type.PostDec, expr)
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

    private fun type() = Type(currentPosition, expect(Id).text)
}