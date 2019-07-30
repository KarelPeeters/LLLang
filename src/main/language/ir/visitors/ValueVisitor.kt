package language.ir.visitors

import language.ir.BasicBlock
import language.ir.Constant
import language.ir.Function
import language.ir.Instruction
import language.ir.ParameterValue
import language.ir.UndefinedValue
import language.ir.Value
import language.ir.VoidValue

interface ValueVisitor<T> {
    operator fun invoke(value: Value): T = when (value) {
        is Function -> invoke(value)
        is BasicBlock -> invoke(value)
        is Instruction -> invoke(value)
        is ParameterValue -> invoke(value)
        is Constant -> invoke(value)
        is UndefinedValue -> invoke(value)
        is VoidValue -> invoke(value)
        else -> error("Unknown value type ${value::class}")
    }

    operator fun invoke(value: Function): T
    operator fun invoke(value: BasicBlock): T

    operator fun invoke(value: Instruction): T
    operator fun invoke(value: ParameterValue): T

    operator fun invoke(value: Constant): T
    operator fun invoke(value: UndefinedValue): T
    operator fun invoke(value: VoidValue): T
}