package language.ir.support

import language.ir.*
import language.ir.Function
import java.util.*

object BasicSchedule {
    /**
     * Build a basic [Instruction] schedule, intended to be used in the string representation of functions.
     * Works for (most) invalid programs as well to facilitate debugging.
     */
    fun build(function: Function): Map<Region, List<Instruction>> {
        val domInfo = DominatorInfo(function)

        val schedule = Visitor.findRegions(function).associateWith { mutableListOf<Instruction>() }
        val terminators = mutableListOf<Terminator>()

        val toVisit = ArrayDeque<Node>()
        val lastPossibleRegion = mutableMapOf<Node, Region>()

        /** update lastPossibleRegion: [node] is now (also) required in [region] */
        fun requireInRegion(node: Node, region: Region) {
            if (node is Phi || node is Region || node is Function)
                return

            val prev = lastPossibleRegion[node]
            val new = domInfo.commonDominator(prev ?: region, region)
            lastPossibleRegion[node] = new
            if (prev != new)
                toVisit += node
        }

        //set initial requirements
        for (node in Visitor.findInnerNodes(function)) {
            when (node) {
                is Phi -> {
                    schedule.getValue(node.region) += node
                    for ((pred, value) in node.region.predecessors zip node.values)
                        requireInRegion(value, pred.from ?: continue)
                }
                is Terminator -> {
                    terminators += node
                    for (op in node.operands())
                        requireInRegion(op, node.from)
                }
            }
        }

        //main loop, visit updated nodes and update their operands until nothing changes
        while (true) {
            val curr = toVisit.poll() ?: break
            val region = lastPossibleRegion.getValue(curr)
            for (op in curr.operands())
                requireInRegion(op, region)
        }

        //put instructions in the schedule
        for ((node, region) in lastPossibleRegion)
            if (node is Instruction)
                schedule.getValue(region) += node

        //put terminators in the schedule
        for (term in terminators)
            schedule.getValue(term.from) += term

        return schedule
    }
}