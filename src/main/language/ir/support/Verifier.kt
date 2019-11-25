package language.ir.support

import language.ir.*
import language.ir.Function

object Verifier {
    fun verifyProgram(program: Program) {
        //typecheck
        for (node in Visitor.findNodes(program)) {
            node.typeCheck()
        }

        val userInfo = UserInfo(program)

        for (function in Visitor.findFunctions(program)) {
            //function return types
            function.ret?.let { ret ->
                check(ret.types == function.type.returns) {
                    "expected return types ${function.type.returns}, got ${ret.types}"
                }
            }

            //dominance
            val domInfo = DominatorInfo(function)
            checkFunction(function, userInfo, domInfo)
        }
    }

    private fun checkFunction(function: Function, userInfo: UserInfo, domInfo: DominatorInfo) {
        val checking = mutableSetOf<Node>()

        fun checkDominates(value: Node, region: Region) {
            check(value !in checking) { "Found cycle: $checking" }
            checking += value

            when (value) {
                //shouldn't be used these
                is Program, is Region, is StartControl, is PlaceHolder ->
                    error("Unexpected usage of ${value::class.simpleName}")
                //constants trivially dominate everything
                is Constant, is Undef -> Unit
                //can only use parameters of the current function
                is Parameter -> check(value in function.parameters) {
                    "Using parameter $value not declared by this function"
                }
                //phi dominates if its region dominates
                is Phi -> check(domInfo.dominates(value.region, region)) {
                    "Phi $value does not dominate all users"
                }
                //other nodes dominate if all of their operands dominate
                else -> {
                    for (operand in value.operands())
                        checkDominates(operand, region)
                }
            }

            checking -= value
        }

        for (node in Visitor.findInnerNodes(function)) {
            if (node.type == ControlType)
                check(userInfo[node].size <= 1) { "control nodes can only be used once" }
            if (node.type == RegionType)
                check(userInfo[node].count { it is Terminator } <= 1) { "regions can only be used by one terminator" }

            when (node) {
                is Phi -> {
                    //phi values must dominate the predecessor block
                    for ((pred, value) in node.zippedValues())
                        checkDominates(value, pred.from ?: error("Start as phi predecessor"))
                }
                is Terminator -> {
                    //terminator operands must dominate the from block
                    for (op in node.operands())
                        if (op != node.from)
                            checkDominates(op, node.from)
                }
            }
        }
    }
}
