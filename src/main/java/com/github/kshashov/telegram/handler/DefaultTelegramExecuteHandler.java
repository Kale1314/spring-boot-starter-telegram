package com.github.kshashov.telegram.handler;

import com.github.kshashov.telegram.config.TelegramBotGlobalProperties;
import com.github.kshashov.telegram.handler.processor.TelegramCallback;
import com.github.kshashov.telegram.handler.processor.Template;
import com.github.kshashov.telegram.metrics.MetricsService;
import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class DefaultTelegramExecuteHandler implements TelegramExecuteHandler {
    private final TelegramBot defaultBot;
    private final ITemplateEngine templateEngine;

    private final TelegramBotGlobalProperties globalProperties;

    private final MetricsService metricsService;


    /**
     * sends the request to the Telegram.
     *
     * @param baseRequest request
     */
    @Override
    public void execute(BaseRequest baseRequest) {
        execute(defaultBot, baseRequest);
    }

    /**
     * sends the request to the Telegram.
     *
     * @param baseRequest request
     */
    @Override
    public void execute(TelegramCallback baseRequest) {
        execute(defaultBot, baseRequest);
    }

    /**
     * sends the request by template to the Telegram.
     *
     * @param chatId   chat id
     * @param template template
     */
    @Override
    public <T> void execute(String chatId, Template<T> template) {
        this.execute(defaultBot, chatId, template);
    }

    /**
     * sends the request to the Telegram.
     *
     * @param bot         bot
     * @param baseRequest request
     */
    @Override
    public void execute(TelegramBot bot, BaseRequest baseRequest) {
        this.execute(bot, new TelegramCallback(baseRequest, new Callback() {
            @Override
            public void onResponse(BaseRequest request, BaseResponse response) {
                if (log.isDebugEnabled()) {
                    log.debug("发送成功：{} {}", request, response);
                }
            }

            @Override
            public void onFailure(BaseRequest request, IOException e) {
                log.error("发送失败", e);
            }
        }));
    }

    /**
     * sends the request to the Telegram.
     *
     * @param bot         bot
     * @param baseRequest request
     */
    @Override
    public void execute(TelegramBot bot, TelegramCallback baseRequest) {
        bot.execute(baseRequest.getRequest(), new Callback() {
            @Override
            public void onResponse(BaseRequest request, BaseResponse response) {
                baseRequest.onResponse(request, response);
                globalProperties.getResponseCallback().onResponse(request, response);
                log.debug("{} request was successfully executed", baseRequest);
            }

            @Override
            public void onFailure(BaseRequest request, IOException e) {
                baseRequest.onFailure(request, e);
                globalProperties.getResponseCallback().onFailure(request, e);
                metricsService.onUpdateError();
                log.error(baseRequest + " request was failed", e);
            }
        });
    }

    /**
     * sends the request by template to the Telegram.
     *
     * @param bot      bot
     * @param chatId   chat id
     * @param template template
     */
    @Override
    public <T> void execute(TelegramBot bot, String chatId, Template<T> template) {
        if (templateEngine == null) {
            throw new RuntimeException("未初始化模板引擎");
        }
        Context context = new Context();
        context.setVariable("model", template.getModel());
        String message = templateEngine.process(template.getTemplate(), context);
        if (message == null) {
            return;
        }
        SendMessage sendMessage = new SendMessage(chatId, message)
                .parseMode(template.parseMode())
                .disableWebPagePreview(true)
                .disableNotification(template.isDisableNotification());

        if (template.getKeyboard() != null) {
            sendMessage.replyMarkup(template.getKeyboard());
        }
        this.execute(sendMessage);
    }


}
