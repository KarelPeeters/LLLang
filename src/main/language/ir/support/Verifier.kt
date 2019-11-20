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
            for (ret in Visitor.findReturns(function)) {
                check(ret.types == function.type.returns) { "expected return types ${function.type.returns}, got ${ret.types}" }
            }

            //dominance
            val domInfo = DominatorInfo(function)
            checkDominance(function, userInfo, domInfo)
        }
    }

    private fun checkDominance(function: Function, userInfo: UserInfo, domInfo: DominatorInfo) {
        val checking = mutableSetOf<Node>()

        fun checkDominates(value: Node, region: Region) {
            check(value !in checking) { "Found cycle including $value" }
            checking += value

            when (value) {
                //normal nodes shouldn't use these
                is Program, is Region, is Terminator -> error("Unexpected usage of ${value::class.simpleName}")
                is PlaceHolder -> error("Usage of placeholder")
                //can only use parameters of the current function
                is Parameter -> check(value in function.parameters) { "Using parameter $value not declared by this function" }
                //phi dominates if its region dominates
                is Phi -> check(domInfo.dominates(value.region, region)) { "Phi $value does not dominate all users" }
                //constants trivially dominate everything
                is Constant, is Undef -> Unit
                //other nodes dominate if all of their operands dominate
                else -> {
                    for (operand in value.operands())
                        checkDominates(operand, region)
                }
            }

            checking -= value
        }

        val domRequirements = Visitor.findInnerNodes(function).asSequence().flatMap { node ->
            when (node) {
                is Phi -> {
                    //phi values must dominate the predecessor block
                    node.values.asSequence().map { (region, node) -> node to region }
                }
                is Terminator -> {
                    //non-region terminator operands must dominate the region that uses the terminator
                    //allow multiple regions to use the same terminator, unclear whether this is actually useful
                    val regions = userInfo[node].filterIsInstance<Region>()
                    (node.operands() - node.successors()).asSequence().flatMap { operand ->
                        regions.asSequence().map { region -> operand to region }
                    }
                }
                else -> {
                    //other nodes don't have any dominance requirements
                    emptySequence()
                }
            }
        }

        for ((value, region) in domRequirements) {
            checkDominates(value, region)
        }
    }
}
