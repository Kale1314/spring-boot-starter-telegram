package com.github.kshashov.telegram.handler.processor.response;

import com.github.kshashov.telegram.api.TelegramRequest;
import com.github.kshashov.telegram.handler.processor.Template;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@RequiredArgsConstructor
public class BotTemplateMethodProcessor implements BotHandlerMethodReturnValueHandler {
    private final ITemplateEngine templateEngine;

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return Template.class.isAssignableFrom(paramType);
    }

    @Nullable
    @Override
    public BaseRequest handleReturnValue(@Nullable Object returnValue, MethodParameter returnType, TelegramRequest telegramRequest) {
        if (returnValue == null) {
            log.error("Current request is return null");
            return null;
        }
        Template<?> template = (Template<?>) returnValue;
        Context context = new Context();
        context.setVariable("model", template.getModel());
        context.setVariable("request", telegramRequest);
        String outputValue = templateEngine.process(template.getTemplate(), context);
        if (outputValue != null) {
            if (telegramRequest.getChat() != null) {
                return build(template, telegramRequest, outputValue);
            }
        }
        return null;
    }

    private BaseRequest build(Template<?> template, TelegramRequest telegramRequest, String message) {
        if (template.getEdit() != null) {
            return buildEdit(template, telegramRequest, message);
        } else {
            Integer messageId = messageId(telegramRequest);
            SendMessage sendMessage = new SendMessage(telegramRequest.getChat().id(), message)
                    .parseMode(template.parseMode())
                    .disableWebPagePreview(true)
                    .disableNotification(template.isDisableNotification());
            if (messageId != null) {
                sendMessage.replyToMessageId(messageId);
            }
            if (template.getKeyboard() != null) {
                sendMessage.replyMarkup(template.getKeyboard());
            }
            return sendMessage;
        }
    }


    private BaseRequest buildEdit(Template<?> template, TelegramRequest telegramRequest, String message) {
        Long chatId = telegramRequest.getChat().id();
        Integer messageId = messageId(telegramRequest);
        if (template.getEdit() == Template.Edit.REPLY_MARKUP) {
            return new EditMessageReplyMarkup(chatId, messageId)
                    .replyMarkup((InlineKeyboardMarkup) template.getKeyboard());
        }
        EditMessageText editMessageText = new EditMessageText(chatId, messageId, message)
                .parseMode(template.parseMode())
                .disableWebPagePreview(true);
        if (template.getKeyboard() != null
                && template.getKeyboard() instanceof InlineKeyboardMarkup keyboardMarkup) {
            editMessageText.replyMarkup(keyboardMarkup);
        }
        return editMessageText;
    }


    private Integer messageId(TelegramRequest telegramRequest) {
        return switch (telegramRequest.getMessageType()) {
            case ANY, POLL, UNSUPPORTED, MY_CHAT_MEMBER, PRECHECKOUT_QUERY, SHIPPING_QUERY, CHOSEN_INLINE_RESULT, INLINE_QUERY, EDITED_CHANNEL_POST, CHANNEL_POST ->
                    null;
            case MESSAGE -> telegramRequest.getMessage().messageId();
            case EDITED_MESSAGE -> telegramRequest.getUpdate().editedMessage().messageId();
            case CALLBACK_QUERY -> telegramRequest.getUpdate().callbackQuery().message().messageId();
        };
    }
}
