package language.optimizer.passes

import language.ir.Call
import language.ir.Function
import language.ir.Program
import language.ir.Return
import language.ir.VoidType
import language.ir.VoidValue
import language.optimizer.OptimizerContext
import language.optimizer.ProgramPass

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
            //no point modifying functions that aren't used
            if (!function.isUsed()) continue

            val callers = function.usersIfOnlyCallTarget() ?: continue
            val paramCount = function.parameters.size

            //find unused parts of signature
            val usedParams = List(paramCount) { function.parameters[it].isUsed() }
            val usedReturn = function.returnType == VoidType || callers.any { it.isUsed() }

            //check if there's anyhting to change
            val replacement = removeDeadSignature(function, usedParams, usedReturn)

            //put new function in program
            funcIter.set(replacement)

            changed()
        }
    }
}

fun removeDeadSignature(function: Function, usedParams: List<Boolean>, usedReturn: Boolean): Function {
    //nothing to do
    if (usedParams.all { it } && usedReturn)
        return function

    //create new function
    val newParameters = function.parameters.filterIndexed { i, _ -> usedParams[i] }
    val newReturnType = if (usedReturn) function.returnType else VoidType
    val replacement = function.changedSignature(newParameters, newReturnType)

    //fix calls
    val calls = function.usersIfOnlyCallTarget() ?: error("Function can only be used as call target")
    for (call in calls) {
        val newArgs = call.arguments.filterIndexed { i, _ -> usedParams[i] }

        if (usedReturn) {
            //only arguments have changed
            call.target = replacement
            call.arguments.clear()
            call.arguments.addAll(newArgs)
        } else {
            //return type has changed, need to make a new Call
            val newCall = Call(call.name, replacement, newArgs)
            val index = call.indexInBlock()
            call.block.add(index, newCall)
            call.deleteFromBlock()
        }
    }

    //fix returns
    if (!usedReturn) {
        for (block in function.blocks)
            (block.terminator as? Return)?.value = VoidValue
    }

    return replacement
}