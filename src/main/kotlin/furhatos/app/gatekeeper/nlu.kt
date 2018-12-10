package furhatos.app.gatekeeper

import furhatos.nlu.Intent
import furhatos.util.Language

class Master : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Master",
            "I'm your master",
            "Your master"
        )
    }
}

class UnwantedPurpose : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "WASP",
            "wasp",
            "paper",
            "paperwork",
            "deadline",
            "help"
        )
    }
}

class WelcomedPurpose : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Beer",
            "League of Legends",
            "Fika"
        )
    }
}