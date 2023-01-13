package com.github.kshashov.telegram.handler;

import com.pengrad.telegrambot.TelegramBot;

/**
 * Is used to listen for telegram events and process them.
 */
public interface TelegramService {

    /**
     * Subscribe on Telegram events.
     */
    void start();

    /**
     * Unsubscribe from Telegram events.
     */
    void stop();


    /**
     * 加载 bot
     *
     * @return bot
     */
    TelegramBot bot();


    /**
     * 加载bot token
     *
     * @return token
     */
    String token();


}
