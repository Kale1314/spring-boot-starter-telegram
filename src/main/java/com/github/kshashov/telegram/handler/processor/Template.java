package com.github.kshashov.telegram.handler.processor;

import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.model.request.ParseMode;
import lombok.Data;

@Data
public class Template<T> {

    private String template;
    private T model;
    private Keyboard keyboard;
    private Edit edit;
    private boolean disableNotification = false;

    public Template(String template, T model) {
        this.template = template;
        this.model = model;
    }

    public <R extends Template<T>> R edit() {
        return this.edit(Edit.MESSAGE);
    }

    public <R extends Template<T>> R edit(Edit edit) {
        this.edit = edit;
        return (R) this;
    }

    public <R extends Template<T>> R disableNotification(boolean disableNotification) {
        this.disableNotification = disableNotification;
        return (R) this;
    }


    public <R extends Template<T>> R keyboard(Keyboard keyboard) {
        this.keyboard = keyboard;
        return (R) this;
    }

    public ParseMode parseMode() {
        return null;
    }


    public enum Edit {
        MESSAGE, REPLY_MARKUP
    }

    public static class Html<T> extends Template<T> {

        public Html(String template, T model) {
            super(template, model);
        }

        @Override
        public ParseMode parseMode() {
            return ParseMode.HTML;
        }
    }

    public static class Markdown<T> extends Template<T> {
        public Markdown(String template, T model) {
            super(template, model);
        }

        @Override
        public ParseMode parseMode() {
            return ParseMode.MarkdownV2;
        }
    }
}
