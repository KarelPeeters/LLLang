package language.optimizer.passes

import language.ir.*
import language.ir.Function
import language.ir.support.UserInfo
import language.optimizer.FunctionPass
import language.optimizer.OptimizerContext

/**
 * Converts [Alloc]s only locally used in [Load]s and [Store]s into [Phi]s.
 */
object AllocToPhi : FunctionPass {
    override fun OptimizerContext.optimize(function: Function) {
        //make sure function has a single mem parameter
        val startMem = function.parameters.singleOrNull { it.type == MemType } ?: return

        val userInfo = UserInfo(function)
        val allocs = takeConvertableAllocs(userInfo, startMem)

        if (allocs.isEmpty()) return
        changed()

        //maps (memPhi, alloc) to valuePhi
        val phis = mutableMapOf<Pair<Phi, Alloc>, Phi>()

        fun findStoreValue(alloc: Alloc, mem: Node): Node = when (mem) {
            startMem -> {
                //reached the start without finding any stores
                Undef(alloc.innerType)
            }
            is LoadAfterMem -> {
                //load doesn't affect value, continue searching before
                findStoreValue(alloc, mem.load.beforeMem)
            }
            is Store -> {
                if (mem.address == alloc.result) {
                    //if this store in into the current alloc, look at the value
                    val value = mem.value
                    val addressAlloc = ((value as? LoadResult)?.load?.address as? AllocResult)?.alloc

                    if (addressAlloc != null && addressAlloc in allocs) {
                        //if the value is a load from an alloc that will be also be replaced recurse for that alloc
                        findStoreValue(addressAlloc, value.load.beforeMem)
                    } else {
                        //otherwise this is the final value
                        mem.value
                    }
                } else {
                    //store with different address doesn't affect value, continue searching before
                    findStoreValue(alloc, mem.beforeMem)
                }
            }
            is CallResult -> {
                //the alloc is guaranteed local to this function, so called functions can't affect the value
                findStoreValue(alloc, mem.call.arguments.single { it.type == MemType })
            }
            is Phi -> {
                //create a new phi for the value based on this mem phi
                phis[mem to alloc] ?: run {
                    val phi = Phi(alloc.innerType, mem.region)
                    phi.name = alloc.result.name

                    //store the phi now, it might be used as one of its own values
                    phis[mem to alloc] = phi

                    //look for corresponding values
                    for ((pred, value) in mem.values) {
                        phi.values.getOrPut(pred) {
                            findStoreValue(alloc, value)
                        }
                    }
                    phi
                }
            }
            else -> error("unknown mem source $mem")
        }

        for (alloc in allocs) {
            for (load in userInfo[alloc.result].filterIsInstance<Load>()) {
                userInfo.replaceNode(load.result, findStoreValue(alloc, load.beforeMem))
                userInfo.replaceNode(load.afterMem, load.beforeMem)
            }

            val stores = userInfo[alloc.result].filterIsInstance<Store>()
            for (store in stores) {
                userInfo.replaceNode(store, store.beforeMem)
            }
        }
    }
}

/**
 * Find all allocs that can be converted to SSA form.
 * Only looks at allocs that are part of the [start] Mem chain until it splits.
 * Convertable allocs are are taken out of this chain and returned.
 */
private fun takeConvertableAllocs(userInfo: UserInfo, start: Node): Set<Alloc> {
    val allocs = mutableSetOf<Alloc>()

    //collect allocs
    var nextMem = start
    while (true) {
        val alloc = userInfo[nextMem].singleOrNull() as? Alloc ?: break
        nextMem = alloc.afterMem

        if (userInfo[alloc.result].all { (it is Store && it.value != alloc) || it is Load })
            allocs += alloc
    }

    //take allocs out of the mem chain
    for (alloc in allocs)
        userInfo.replaceNode(alloc.afterMem, alloc.beforeMem)

    return allocs
}
