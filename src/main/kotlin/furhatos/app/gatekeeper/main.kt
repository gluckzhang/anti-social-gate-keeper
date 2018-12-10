package furhatos.app.gatekeeper

import furhatos.flow.kotlin.Flow
import furhatos.skills.Skill

class Gatekeeper : Skill() {
    override fun start() {
        Flow().run(Idle)
    }
}

fun main(args: Array<String>) {
    Skill.main(args)
}
