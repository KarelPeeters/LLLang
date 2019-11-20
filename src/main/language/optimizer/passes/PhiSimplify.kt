package language.optimizer.passes

import language.ir.*
import language.ir.Function
import language.ir.support.UserInfo
import language.ir.support.Visitor
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext
import java.util.*

/**
 * Simplifies [Phi]s:
 *   - values coming from unreachable regions are removed
 *   - phis with only a single possible value are replaced by that value
 */
object PhiSimplify : FunctionPass {
    override fun OptimizerContext.optimize(function: Function) {
        val userInfo = UserInfo(function)
        val phis = Visitor.findInnerNodes(function).filterIsInstance<Phi>()
        val regions = Visitor.findRegions(function)

        //remove unreachable regions from phi values
        for (phi in phis) {
            if (phi.values.entries.removeIf { it.key !in regions })
                changed()
        }

        //replace phis that are undefined or have a single value
        val lattice = calculateLattice(phis)
        for ((phi, value) in lattice) {
            if (value != null) {
                userInfo.replaceNode(phi, value)
                changed()
            }
        }
    }

    private fun calculateLattice(phis: List<Phi>): Map<Phi, Node?> {
        val phiUserInfo = phis.associateWith { value ->
            phis.filter { user -> value in user.values.values }
        }

        val lattice: MutableMap<Phi, Node?> = phis.associateWithTo(mutableMapOf()) { Undef(it.type) }

        val toVisit = ArrayDeque<Phi>()
        toVisit.addAll(phis)

        while (true) {
            val phi = toVisit.poll() ?: break

            val prev = lattice.getValue(phi)
            val new = phi.values.values.map {
                if (it is Phi) lattice.getValue(it) else it
            }.merge(phi.type)

            if (prev != new) {
                lattice[phi] = new
                toVisit.addAll(phiUserInfo.getValue(phi))
            }
        }
        return lattice
    }
}

private fun merge(left: Node?, right: Node?): Node? {
    if (left == right) return left

    if (left is Undef) return right
    if (right is Undef) return left

    return null
}

private fun Iterable<Node?>.merge(type: Type): Node? = fold<Node?, Node?>(Undef(type), ::merge)