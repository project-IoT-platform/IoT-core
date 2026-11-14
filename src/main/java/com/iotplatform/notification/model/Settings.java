package com.iotplatform.notification.model;

/** Общие норма и допустимое отклонение (для устройств без своей строки в таблице rute). null = не задано. */
public record Settings(Double norm, Double deviation) {}
