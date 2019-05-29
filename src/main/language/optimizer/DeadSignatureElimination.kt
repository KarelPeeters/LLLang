package language.optimizer

import language.ir.Call
import language.ir.ParameterValue
import language.ir.Program
import language.util.castIfAllInstance

object DeadSignatureElimination : ProgramPass {
    override fun ProgramContext.optimize(program: Program) {
        val funcIter = program.functions.listIterator()

        for (function in funcIter) {
            val callers = function.users.castIfAllInstance<Call>() ?: continue

            //unused parameters
            val removedIndiches = mutableListOf<Int>()
            val keptParameters = mutableListOf<ParameterValue>()

            for ((i, param) in function.parameters.withIndex()) {
                if (param.users.isEmpty())
                    removedIndiches += i
                else
                    keptParameters += param
            }

            if (removedIndiches.isEmpty())
                continue

            //create new function
            val replacement = function.changedSignature(keptParameters, function.returnType)
            for ((old, new) in keptParameters zip replacement.parameters)
                old.replaceWith(new)
            funcIter.set(replacement)

            //change callers
            for (call in callers) {
                call.target = replacement
                for (i in removedIndiches.asReversed())
                    call.arguments.removeAt(i)
            }

            changed()
        }
    }
}
