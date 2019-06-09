package language.ir

abstract class Node {
    private var deleted: Boolean = false

    fun delete() {
        check(!deleted)
        deleted = true

        if (this is User)
            this.dropOperands()
    }
}