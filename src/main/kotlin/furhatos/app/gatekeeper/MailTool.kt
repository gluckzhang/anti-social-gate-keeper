package furhatos.app.gatekeeper

import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.HtmlEmail

fun send(name: String?, message: String?, type: String?) {
    val senderEmail = "antisocial.gatekeeper@gmail.com"
    val password = "TuPerdsTonSangFroid"
    val toMail = "longz@kth.se"

    val email = HtmlEmail()
    email.hostName = "smtp.googlemail.com"
    email.setSmtpPort(465)
    email.setAuthenticator(DefaultAuthenticator(senderEmail, password))
    email.isSSLOnConnect = true
    email.setFrom(senderEmail)
    email.addTo(toMail)
    email.subject = "[AntiSocial GateKeeper] ${type} interaction from ${name}."
    email.setHtmlMsg("<html><h1>${type} interaction from ${name}</h1><p>${message}</p></html>")
    email.send()
}