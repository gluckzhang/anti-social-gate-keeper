package furhatos.app.gatekeeper

import furhatos.flow.kotlin.*
import furhatos.flow.kotlin.voice.PollyVoice
import furhatos.gestures.Gestures
import furhatos.nlu.common.*
import furhatos.util.Language
import furhatos.records.User
import furhatos.util.Gender

// Variables

val maxRounds = 5
var rounds = 0
var shouldChangeUser = true
var playing = false
val voice = PollyVoice("Joanna", Gender.FEMALE, Language.ENGLISH_US)

val Interaction : State = state {

    onResponse<RequestRules> {
        furhat.say("You get $maxRounds questions, each with 4 options. You get one point for each correct answer.")
        if (users.count > 1) {
            furhat.say("If you answer wrong, the question will go over to the next person")
        }
        reentry()
    }

    onResponse<Goodbye> {
        furhat.say("It was nice talking to you. Goodbye")
        goto(Idle)
    }

    onUserEnter(instant = true) {
        furhat.glance(it.id)
    }

    onUserLeave(instant = true) {
        it.quiz.playing = false
        if (users.current?.id == it.id) {
            furhat.stopSpeaking()
            if (users.playing().count() > 0) {
                furhat.attend(users.nextPlaying())
                goto(NewQuestion)
            } else {
                goto(Idle)
            }
        }
    }
}

val Idle: State = state {
    init {
        furhat.voice = voice
    }

    onEntry {
        /*
            Loop through all (potentially) interested users.
            Goto calls are used since users may enter and leave
            while we are querying other users and we want to
            ask all users before moving on. I.e we want to update the
            users.interested() list of users.
          */
        users.interested().forEach {
            furhat.attend(it)
            goto(QueryPerson(it))
        }
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

    onUserLeave(instant = true) {
        furhat.say("Thanks for your visit, goodbye!")
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
        goto(QueryPurpose)
    }

}

val QueryPurpose = state(parent = Interaction) {
    onEntry {
        furhat.ask("What's the purpose of your visit?")
    }

    onResponse<UnwantedPurpose> {
        furhat.say("Thanks for your visit.")
        furhat.say("I'll let the master know when he has time!")
        goto(Idle)
    }

    onResponse<WelcomedPurpose> {
        furhat.say("Sure!")
        furhat.say("I'll notify the master right away!")
        goto(Idle)
    }
}

val NewQuestion = state(parent = Interaction) {
    onEntry {
        /*
            If more than one player, we determine what user to target next here, based on the shouldChangeUser boolean
         */
        if (users.playing().count() > 1) {
            if (shouldChangeUser) {
                furhat.attend(users.nextPlaying())
                random(
                    { furhat.say("The next one is for you") },
                    { furhat.say("For you now") },
                    { furhat.say("Now for you") }
                )
            }
            else {
                shouldChangeUser = true
                random(
                    { furhat.say("You get to continue") },
                    { furhat.say("Next one coming up") },
                    { furhat.say("Here's another one") }
                )
            }
        }

        // Ask new question
        QuestionSet.next()
        goto(AskQuestion)
    }
}

val AskQuestion : State = state(parent = Interaction) {
    var failedAttempts = 0

    onEntry {
        failedAttempts = 0

        // Set speech rec phrases based on the current question's answers
        furhat.setSpeechRecPhrases(QuestionSet.current.phrases)

        // Ask the question followed by the options
        furhat.ask(QuestionSet.current.text + " " + QuestionSet.current.getOptionsString())
    }

    // Here we re-state the question
    onReentry {
        failedAttempts = 0
        furhat.ask("The question was, ${QuestionSet.current.text} ${QuestionSet.current.getOptionsString()}")
    }

    // User is answering with any of the alternatives
    onResponse<AnswerOption> {
        val answer = it.intent

        // If the user answers correct, we up the user's score and congratulates the user
        if (answer.correct) {
            furhat.gesture(Gestures.Smile)
            users.current.quiz.score++
            random(
                { furhat.say("Great! That was the ${voice.emphasis("right")}  answer, you now have a score of ${users.current.quiz.score}") },
                { furhat.say("that was ${voice.emphasis("correct")}, you now have a score of ${users.current.quiz.score}") }
            )
            /*
            If the user answers incorrect, we give another user the chance of answering if one is present in the game.
            If we indeed ask another player, the furhat.ask() interrupts the rest of the handler.
             */
        } else {
            furhat.gesture(Gestures.BrowFrown)
            furhat.say("Sorry, that was ${voice.emphasis("not")} correct")

            // Keep track of what users answered what question so that we don't ask the same user
            users.current.quiz.questionsAsked.add(QuestionSet.current.text)

            /* Find another user that has not answered this question and if so, asks them.
             For the flow of the skill, we will continue asking the new user the next question through the
             shouldChangeUser = false flag.
             */
            val availableUsers = users.notQuestioned(QuestionSet.current.text)
            if (!availableUsers.isEmpty()) {
                furhat.attend(availableUsers.first())
                shouldChangeUser = false
                furhat.ask("Maybe you know the answer?")
            }
        }

        // Check if the game has ended and if not, goes to a new question
        if (++rounds >= maxRounds) {
            furhat.say("That was the last question")
            goto(EndGame)
        } else {
            goto(NewQuestion)
        }
    }

    // The users answers that they don't know
    onResponse<DontKnow> {
        furhat.say("Too bad. Here comes the next question")
        goto(NewQuestion)
    }

    onResponse<RequestRepeat> {
        reentry()
    }

    onResponse<RequestRepeatQuestion> {
        furhat.gesture(Gestures.BrowRaise)
        furhat.ask(QuestionSet.current.text)
    }

    // The user wants to hear the options again
    onResponse<RequestRepeatOptions> {
        furhat.gesture(Gestures.Surprise)
        random(
            { furhat.ask("They are ${QuestionSet.current.getOptionsString()}")  },
            { furhat.ask(QuestionSet.current.getOptionsString())  }
        )
    }

    // If we don't get any response, we assume the user was too slow
    onNoResponse {
        random(
            { furhat.say("Too slow! Here comes the next question") },
            { furhat.say("A bit too slow amigo! Get ready for the next question") }
        )
        goto(NewQuestion)
    }

    /* If we get a response that doesn't map to any alternative or any of the above handlers,
        we track how many times this has happened in a row and give them two more attempts and
        finally moving on if we still don't get it.
     */
    onResponse {
        failedAttempts++
        when (failedAttempts)  {
            1 -> furhat.ask("I didn't get that, sorry. Try again!")
            2 -> {
                furhat.say("Sorry, I still didn't get that")
                furhat.ask("The options are ${QuestionSet.current.getOptionsString()}")
            }
            else -> {
                furhat.say("Still couldn't get that. Let's try a new question")
                shouldChangeUser = false
                goto(NewQuestion)
            }
        }
    }
}

// End of game, announcing results
val EndGame : State = state(parent = Interaction) {
    var skipNext = false

    onEntry {
        playing = false

        // If multiple players, we rank the players on points and congratulate the winner. A special case is when we have a tie.
        if (users.playing().count() > 1) {
            // Sort users by score
            users.playing().sortedByDescending {
                it.quiz.score
            }.forEachIndexed { index, user ->
                // The winner(s)
                if (index == 0) {
                    val highestScore = user.quiz.score
                    val usersWithHighestScore = users.playing().filter {
                        it.quiz.score == highestScore
                    }
                    // Check if we have more than one user with the highest score and if so announce a tie
                    if (usersWithHighestScore.count() > 1) {
                        furhat.say("Wow, we have a tie! You each have $highestScore points", async = true)
                        delay(500)
                        usersWithHighestScore.forEach {
                            furhat.attend(it)
                            delay(700)
                        }
                        // If a tie, we skip the next user since we've already adressed them above
                        skipNext = true
                    }
                    // A single winner exists
                    else {
                        furhat.attend(user)
                        furhat.say("Congratulations! You won with ${user.quiz.score} points")
                    }
                }
                // Every other subsequent player
                else {
                    if (!skipNext) {
                        skipNext = false
                        furhat.attend(user)
                        furhat.say("And you got ${user.quiz.score} points")
                    }
                }
                delay(300)
            }
        }
        // Only one player, we simply announce the score
        else {
            furhat.say("You got ${users.current.quiz.score} points.")
        }

        furhat.say("Thanks for playing!")

        // Resetting user state variables
        users.playing().forEach {
            it.quiz.playing = false
            it.quiz.played = true
            it.quiz.lastScore = it.quiz.score
        }

        delay(1000)

        goto(Idle)
    }
}


