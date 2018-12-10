package furhatos.app.gatekeeper

import furhatos.flow.kotlin.*
import furhatos.flow.kotlin.voice.PollyVoice
import furhatos.util.Language
import furhatos.records.User
import furhatos.util.Gender

// Variables
val voice = PollyVoice("Joanna", Gender.FEMALE, Language.ENGLISH_US)
var names : HashMap<String, String> = HashMap()

val Interaction : State = state {
    onUserEnter(instant = true) {
        furhat.glance(it.id)
    }
}

val Idle: State = state {
    init {
        furhat.voice = voice
    }

    onUserEnter(instant = true) {
        /* Say hi and query a new user if it's the only interested user.
            Other interested users would already be greeted at this point.
            If not, we glance at the user and keep doing whatever we were doing.
         */
        furhat.attend(it.id)
        furhat.say("Hello!")
        goto(QueryPerson(it))
    }
}

fun QueryPerson(user: User) = state(parent = Interaction) {
    onEntry {
        furhat.ask("Please say your name?")
    }

    onResponse<Master> {
        furhat.say("Hello master!")
        goto(Idle)
    }

    onResponse {
        names.put(user.id, it.text)
        furhat.say("Hello ${it.text}")
        goto(QueryPurpose(user))
    }

}

fun QueryPurpose(user : User) = state(parent = Interaction) {
    onEntry {
        furhat.ask("What's the purpose of your visit?")
    }

    onResponse<UnwantedPurpose> {
        furhat.say("Thanks for your visit.")
        furhat.say("I'll let the master know when he has time!")
        goto(UnwantedExit)
    }

    onResponse<WelcomedPurpose> {
        furhat.say("Sure!")
        furhat.say("I'll notify the master right away!")
        send(names.get(user.id), it.text)
        goto(WelcomedExit(user))
    }

    onResponse {
        furhat.say("Unrecognized actions.")
        goto(SendMessage(user))
    }
}

fun WelcomedExit (user : User) = state(parent = Interaction) {
    onEntry {
        System.out.println(user.id)
    }

    onUserLeave {
        furhat.say("Bye, ${names.get(user.id)}")
        furhat.say("It was a pleasure seeing you!")
        goto(Idle)
    }
}

val UnwantedExit = state(parent = Interaction) {
    onEntry {
        random (
            { furhat.say("Thanks for your visit, please leave!") },
            { furhat.say("Bye bye now!") }
        )
        goto(Idle)
    }
}

fun SendMessage (user : User) = state(parent = Interaction) {
    onEntry {
        furhat.ask("What message do you want to send to the master?")
    }

    onResponse {
        System.out.println(names.get(user.id))
        System.out.println(it.text)
        send(names.get(user.id), it.text)
        goto(UnwantedExit)
    }
}
