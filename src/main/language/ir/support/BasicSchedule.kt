package language.ir.support

import language.ir.*
import language.ir.Function
import java.util.*

object BasicSchedule {
    /**
     * Build a basic [Instruction] schedule, inteded use in the string representation of functions.
     */
    fun build(function: Function): Map<Region, List<Instruction>> {
        val domInfo = DominatorInfo(function)

        val schedule = Visitor.findRegions(function).associateWith { mutableListOf<Instruction>() }

        val toVisit = ArrayDeque<Node>()
        val lastPossibleRegion = mutableMapOf<Node, Region>()
        fun requireInRegion(node: Node, region: Region) {
            if (node is Phi || node is Region)
                return

            val prev = lastPossibleRegion[node]
            val new = domInfo.commonDominator(prev ?: region, region)
            lastPossibleRegion[node] = new
            if (prev != new)
                toVisit += node
        }

        for (node in Visitor.findInnerNodes(function)) {
            if (node is Phi) {
                schedule.getValue(node.region) += node
                for ((pred, value) in node.values)
                    requireInRegion(value, pred)
            }
            if (node is Region) {
                for (op in node.terminator.operands())
                    requireInRegion(op, node)
            }
        }

        while (true) {
            val curr = toVisit.poll() ?: break
            val region = lastPossibleRegion.getValue(curr)
            for (op in curr.operands())
                requireInRegion(op, region)
        }

        for ((node, region) in lastPossibleRegion)
            if (node is Instruction)
                schedule.getValue(region) += node

        return schedule
    }
}