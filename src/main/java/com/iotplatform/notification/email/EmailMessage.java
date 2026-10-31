package com.iotplatform.notification.email;

/** Готовое письмо: получатель, тема, HTML-часть и текстовая часть (fallback). */
public record EmailMessage(String to, String subject, String htmlBody, String textBody) {}
