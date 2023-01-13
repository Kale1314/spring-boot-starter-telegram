package com.github.kshashov.telegram.handler;

import com.github.kshashov.telegram.handler.processor.TelegramCallback;
import com.github.kshashov.telegram.handler.processor.Template;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;

import java.util.List;

/**
 * Helper service that processes {@link List} of {@link Update} updates.
 */
public interface TelegramExecuteHandler {

    /**
     * sends the request to the Telegram.
     *
     * @param baseRequest request
     */
    void execute(BaseRequest baseRequest);

    /**
     * sends the request to the Telegram.
     *
     * @param baseRequest request
     */
    void execute(TelegramCallback baseRequest);

    /**
     * sends the request by template to the Telegram.
     *
     * @param chatId   chat id
     * @param template template
     * @param <T>      T
     */
    <T> void execute(String chatId, Template<T> template);


    /**
     * sends the request to the Telegram.
     *
     * @param bot         bot
     * @param baseRequest request
     */
    void execute(TelegramBot bot, BaseRequest baseRequest);

    /**
     * sends the request to the Telegram.
     *
     * @param bot         bot
     * @param baseRequest request
     */
    void execute(TelegramBot bot, TelegramCallback baseRequest);


    /**
     * sends the request by template to the Telegram.
     *
     * @param bot      bot
     * @param chatId   chat id
     * @param template template
     * @param <T>      T
     */
    <T> void execute(TelegramBot bot, String chatId, Template<T> template);
}
