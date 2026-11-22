package com.example.library.notification;

/** Відправка електронного листа (у реальній системі — SMTP / поштовий API). */
public interface MailGateway {

    void send(String to, String subject, String body);
}
