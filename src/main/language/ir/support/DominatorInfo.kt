package language.ir.support

import language.ir.Function
import language.ir.Region

class DominatorInfo(function: Function) {
    private val dominatedBy: Map<Region, Set<Region>> = calcDominatedBy(function)
    private val dominating: Map<Region, Set<Region>> = regions().associateWith { region ->
        regions().filterTo(mutableSetOf()) { cand -> dominates(region, cand) }
    }
    private val domParent: Map<Region, Region?> = regions().associateWith { region ->
        dominators(region).find { cand ->
            cand != region && dominators(region).all { dom -> dom == region || dominates(dom, cand) }
        }
    }

    /** All of the found regions */
    fun regions(): Set<Region> = dominatedBy.keys

    /** The regions that dominate [region] */
    fun dominators(region: Region): Set<Region> = dominatedBy.getValue(region)

    /** The regions that are dominated by [region] */
    fun dominating(region: Region): Set<Region> = dominating.getValue(region)

    /** Whether [region] dominates [other] */
    fun dominates(region: Region, other: Region): Boolean = region in dominators(other)

    /**
     * The parent of [region] in the dominator tree: the single region that strictly dominates [region] and is dominated
     * by all (other) strict dominators of [region].
     * Returns `null` for the entry region as that is the root of the tree.
     */
    fun domParent(region: Region): Region? = domParent.getValue(region)

    /**
     * The set of regions with a predecessor dominated by [region] but who are not themselves dominated by [region].
     * In other words, the set of regions where the dominance of [region] stops.
     */
    fun domFrontier(region: Region): Set<Region> = TODO()

    /**
     * The lowest region that dominates both [first] and [second].
     */
    fun commonDominator(first: Region, second: Region): Region {
        check(first in regions())
        check(second in regions())

        var cand = first
        while (!dominates(cand, second)) {
            //the only region with domParent == null is the entry region, but that dominates [second]
            //and then the loop would have exited already
            cand = domParent(cand)!!
        }
        return cand
    }

    /**
     * The lowest region that dominates all [regions]. Returns `null` if [regions] is empty.
     */
    fun commonDominator(regions: List<Region>): Region? = regions.reduce(this::commonDominator)
}

private fun calcDominatedBy(function: Function): Map<Region, Set<Region>> {
    val regions = Visitor.findRegions(function)

    val result = regions.associateWithTo(mutableMapOf()) { regions.toMutableSet() }
    val entry = regions.single { it.predecessors == listOf(function.start) }

    result[entry] = mutableSetOf(entry)

    do {
        var changed = false
        for ((region, domSet) in result) {
            domSet.remove(region)
            for (pred in region.predecessors)
                if (domSet.retainAll(result.getValue(pred.from ?: continue)))
                    changed = true
            domSet.add(region)
        }
    } while (changed)

    return result
}
