package com.iotplatform.notification.email;

/** Результат рендеринга события в письмо (без адресата). */
public record RenderedEmail(String subject, String html, String text) {}
