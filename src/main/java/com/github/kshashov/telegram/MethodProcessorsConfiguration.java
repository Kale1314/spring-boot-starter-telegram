package com.github.kshashov.telegram;

import com.github.kshashov.telegram.handler.processor.arguments.BotHandlerMethodArgumentResolver;
import com.github.kshashov.telegram.handler.processor.arguments.BotRequestMethodArgumentResolver;
import com.github.kshashov.telegram.handler.processor.arguments.BotRequestMethodPathArgumentResolver;
import com.github.kshashov.telegram.handler.processor.response.BotBaseRequestMethodProcessor;
import com.github.kshashov.telegram.handler.processor.response.BotHandlerMethodReturnValueHandler;
import com.github.kshashov.telegram.handler.processor.response.BotResponseBodyMethodProcessor;
import com.github.kshashov.telegram.handler.processor.response.BotTemplateMethodProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;
import org.thymeleaf.ITemplateEngine;

@Configuration
public class MethodProcessorsConfiguration {

    @Bean
    public BotHandlerMethodArgumentResolver botRequestMethodArgumentResolver() {
        return new BotRequestMethodArgumentResolver();
    }

    @Bean
    public BotHandlerMethodArgumentResolver botRequestMethodPathArgumentResolver() {
        return new BotRequestMethodPathArgumentResolver();
    }

    @Bean
    public BotHandlerMethodReturnValueHandler botBaseRequestMethodProcessor() {
        return new BotBaseRequestMethodProcessor();
    }

    @Bean
    @ConditionalOnMissingBean(BotTemplateMethodProcessor.class)
    @ConditionalOnClass(ITemplateEngine.class)
    public BotHandlerMethodReturnValueHandler botTemplateMethodProcessor(ITemplateEngine templateEngine) {
        return new BotTemplateMethodProcessor(templateEngine);
    }

    @Bean
    public BotHandlerMethodReturnValueHandler botResponseBodyMethodProcessor(ConversionService conversionService) {
        return new BotResponseBodyMethodProcessor(conversionService);
    }


    @Bean
    public ConversionService conversionService() {
        ConversionServiceFactoryBean bean = new ConversionServiceFactoryBean();
        bean.afterPropertiesSet();
        return bean.getObject();
    }
}
