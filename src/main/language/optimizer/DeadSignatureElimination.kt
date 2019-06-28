package language.optimizer

import language.ir.Call
import language.ir.Constant
import language.ir.Function
import language.ir.Program
import language.ir.Return
import language.ir.UnitType
import language.ir.UnitValue
import language.util.mapIfAllInstance

/**
 * This pass eliminates useless parts of function signatures:
 *  * remove unused parameters
 *  * inline parameters that are always passed the same constant
 *  * removed the return type if it's never used by a callsite
 */
object DeadSignatureElimination : ProgramPass() {
    override fun OptimizerContext.optimize(program: Program) {
        val funcIter = program.functions.listIterator()

        for (function in funcIter) {
            //could give problems when finding constants, just skip
            if (!function.isUsed())
                continue

            val callers = function.users.mapIfAllInstance<Call>() ?: continue
            if (callers.any { function in it.arguments }) continue

            val paramCount = function.parameters.size

            //find unused parts of signature
            inlineCallsiteConstantParameters(function, callers)
            val usedParams = BooleanArray(paramCount) { function.parameters[it].isUsed() }
            val unusedReturn = function.returnType != UnitType && callers.none { it.isUsed() }

            //check if there's anyhting to change
            if (usedParams.all { it } && !unusedReturn)
                continue

            //create new function
            val newParameters = function.parameters.filterIndexed { i, _ -> usedParams[i] }
            val newReturnType = if (unusedReturn) UnitType else function.returnType
            val replacement = function.changedSignature(newParameters, newReturnType)

            //fix callers
            for (call in callers) {
                val newArgs = call.arguments.filterIndexed { i, _ -> usedParams[i] }

                if (unusedReturn) {
                    //type has changed, need to make a new Call
                    val newCall = Call(call.name, replacement, newArgs)
                    val index = call.indexInBlock()
                    call.block.add(index, newCall)
                    call.deleteFromBlock()
                } else {
                    call.target = replacement
                    call.arguments.clear()
                    call.arguments.addAll(newArgs)
                }
            }

            //fix returns
            if (unusedReturn) {
                for (block in function.blocks)
                    (block.terminator as? Return)?.value = UnitValue
            }

            //put new function in program
            funcIter.set(replacement)

            changed()
        }
    }

    /**
     * Find parameters that are always passed the same constant and inline that constant
     */
    private fun inlineCallsiteConstantParameters(function: Function, callers: List<Call>) {
        val constants: Array<Constant?> = Array(function.parameters.size) {
            callers[0].arguments[it] as? Constant
        }

        for (caller in callers.asSequence().drop(1)) {
            var anyConstant = false

            for (i in constants.indices) {
                val first = constants[i] ?: continue
                val second = caller.arguments[i]

                constants[i] = if (first == second) {
                    anyConstant = true
                    first
                } else null
            }

            if (!anyConstant) break
        }

        for ((i, const) in constants.withIndex()) {
            if (const != null)
                function.parameters[i].replaceWith(const)
        }
    }
}
