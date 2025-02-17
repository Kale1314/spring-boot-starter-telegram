package com.github.kshashov.telegram;

import com.github.kshashov.telegram.api.TelegramMvcController;
import com.github.kshashov.telegram.api.TelegramSession;
import com.github.kshashov.telegram.config.TelegramBotGlobalProperties;
import com.github.kshashov.telegram.config.TelegramBotGlobalPropertiesConfiguration;
import com.github.kshashov.telegram.config.TelegramBotProperties;
import com.github.kshashov.telegram.handler.*;
import com.github.kshashov.telegram.handler.processor.RequestDispatcher;
import com.github.kshashov.telegram.handler.processor.arguments.BotHandlerMethodArgumentResolver;
import com.github.kshashov.telegram.handler.processor.arguments.BotHandlerMethodArgumentResolverComposite;
import com.github.kshashov.telegram.handler.processor.response.BotHandlerMethodReturnValueHandler;
import com.github.kshashov.telegram.handler.processor.response.BotHandlerMethodReturnValueHandlerComposite;
import com.github.kshashov.telegram.metrics.MetricsConfiguration;
import com.github.kshashov.telegram.metrics.MetricsService;
import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.response.BaseResponse;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.*;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.thymeleaf.ITemplateEngine;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Main configuration for telegram mvc.
 */
@Slf4j
@Configuration
@Import({MethodProcessorsConfiguration.class, MetricsConfiguration.class})
@EnableConfigurationProperties(TelegramConfigurationProperties.class)
public class TelegramAutoConfiguration implements BeanFactoryPostProcessor, EnvironmentAware {
    private Environment environment;

    @Bean
    Javalin javalinServer(@Qualifier("telegramBotPropertiesList") List<TelegramBotProperties> botProperties, TelegramBotGlobalProperties globalProperties) {
        boolean hasWebhook = botProperties.stream().anyMatch(p -> p.getWebhook() != null);
        if (!hasWebhook) return null;

        try {
            Javalin server = Javalin.create().start(globalProperties.getWebserverPort());
            log.info("Javalin server has been started on {} port", globalProperties.getWebserverPort());
            return server;
        } catch (Exception ex) {
            log.error("An unexpected error occured while starting Javalin server", ex);
            return null;
        }
    }

    @Bean
    TelegramUpdatesHandler telegramUpdatesHandler(TelegramBotGlobalProperties globalProperties, RequestDispatcher requestDispatcher, MetricsService metricsService) {
        return new DefaultTelegramUpdatesHandler(requestDispatcher, globalProperties, metricsService);
    }

    @Bean
    @Qualifier("telegramServicesList")
    List<TelegramService> telegramServices(@Qualifier("telegramBotPropertiesList") List<TelegramBotProperties> botProperties, TelegramUpdatesHandler updatesHandler, TelegramBotGlobalProperties globalProperties, Optional<Javalin> server) {
        List<TelegramService> services = botProperties.stream()
                .map(p -> {
                    // Register TelegramBot bean
                    TelegramBot bot = p.getBotBuilder().build();

                    // Let user process bot instance
                    if (globalProperties.getBotProcessors().containsKey(p.getToken())) {
                        globalProperties.getBotProcessors().get(p.getToken()).accept(bot);
                    }

                    // Create bot service
                    if (p.getWebhook() != null) {
                        return new TelegramWebhookService(p, bot, updatesHandler, server.get());
                    } else {
                        return new TelegramPollingService(p, bot, updatesHandler);
                    }
                }).collect(Collectors.toList());

        if (services.isEmpty()) {
            log.error("No bot configurations found");
        } else {
            log.info("Finished Telegram controllers scanning. Found {} bots", services.size());
        }

        return services;
    }

    @Bean
    @Qualifier("telegramBotPropertiesList")
    List<TelegramBotProperties> telegramBotPropertiesList(List<TelegramMvcController> controllers, TelegramBotGlobalProperties globalProperties) {
        return controllers.stream()
                .map(TelegramMvcController::getToken)
                .distinct()
                .map(token -> {
                    TelegramBotProperties.Builder defaultBuilder = createDefaultBotPropertiesBuilder(token, globalProperties);

                    if (globalProperties.getBotProperties().containsKey(token)) {
                        globalProperties.getBotProperties().get(token).accept(defaultBuilder);
                    }
                    return defaultBuilder.build();
                }).collect(Collectors.toList());
    }

    @Bean
    RequestDispatcher requestDispatcher(
            HandlerMethodContainer handlerMethodContainer,
            TelegramSessionResolver sessionResolver,
            TelegramBotGlobalProperties botGlobalProperties,
            MetricsService metricsService) {
        BotHandlerMethodArgumentResolverComposite argumentResolver = new BotHandlerMethodArgumentResolverComposite(botGlobalProperties.getArgumentResolvers());
        BotHandlerMethodReturnValueHandlerComposite returnValueHandler = new BotHandlerMethodReturnValueHandlerComposite(botGlobalProperties.getReturnValueHandlers());

        return new RequestDispatcher(handlerMethodContainer, sessionResolver, argumentResolver, returnValueHandler, metricsService);
    }

    @Bean
    TelegramBotGlobalProperties telegramBotGlobalProperties(
            TelegramBotGlobalPropertiesConfiguration botGlobalPropertiesConfiguration,
            RequestMappingsMatcherStrategy matcherStrategy,
            List<BotHandlerMethodArgumentResolver> argumentResolvers,
            List<BotHandlerMethodReturnValueHandler> returnValueHandlers,
            TelegramConfigurationProperties properties) {
        TelegramBotGlobalProperties.Builder defaultBuilder = createDefaultBotGlobalPropertiesBuilder(matcherStrategy, argumentResolvers, returnValueHandlers, properties);
        botGlobalPropertiesConfiguration.configure(defaultBuilder);
        return defaultBuilder.build();
    }


    @Bean
    TelegramBot telegramBot(TelegramBotGlobalProperties telegramBotGlobalProperties) {
        return new TelegramBot(telegramBotGlobalProperties.getPrimaryBotToken());
    }

    @Bean
    TelegramExecuteHandler telegramExecuteHandler(TelegramBot telegramBot,
                                                  @Autowired(required = false)
                                                  ITemplateEngine templateEngine,
                                                  TelegramBotGlobalProperties telegramBotGlobalProperties,
                                                  MetricsService metricsService) {
        return new DefaultTelegramExecuteHandler(telegramBot, templateEngine, telegramBotGlobalProperties, metricsService);
    }

    @Bean
    TelegramSessionResolver telegramSessionResolver(ApplicationContext context) {
        return new TelegramSessionResolver(context);
    }

    @Bean
    @ConditionalOnMissingBean(TelegramBotGlobalPropertiesConfiguration.class)
    TelegramBotGlobalPropertiesConfiguration telegramBotGlobalPropertiesConfiguration() {
        return builder -> {
        };
    }

    @Bean
    @ConditionalOnMissingBean(RequestMappingsMatcherStrategy.class)
    RequestMappingsMatcherStrategy defaultHandLerMethodsComparator() {
        return new DefaultRequestMappingsMatcherStrategy();
    }

    @Bean
    @Scope(value = TelegramScope.SCOPE, proxyMode = ScopedProxyMode.TARGET_CLASS)
    TelegramSession telegramSession() {
        return new TelegramSession();
    }

    @Bean
    HandlerMethodContainer handlerMethodContainer() {
        return new HandlerMethodContainer();
    }

    @Bean
    TelegramControllerBeanPostProcessor telegramControllerBeanPostProcessor(HandlerMethodContainer handlerMethodContainer, MetricsService metricsService) {
        return new TelegramControllerBeanPostProcessor(handlerMethodContainer, metricsService);
    }

    @Bean
    ApplicationListener<ContextRefreshedEvent> onContextRefreshed(@Qualifier("telegramServicesList") List<TelegramService> telegramServices, TelegramBotGlobalProperties globalProperties, HandlerMethodContainer handlerMethodContainer) {
        handlerMethodContainer.setMatcherStrategy(globalProperties.getMatcherStrategy());
        return event -> telegramServices.forEach((s) -> globalProperties.getTaskExecutor().execute(s::start));
    }

    @Bean
    ApplicationListener<ContextClosedEvent> onContextClosed(TelegramBotGlobalProperties globalProperties, @Qualifier("telegramServicesList") List<TelegramService> telegramServices) {
        return event -> {
            telegramServices.forEach(TelegramService::stop);

            if (globalProperties.getTaskExecutor() != null) {
                log.info("Shutting down ThreadPoolExecutor");
                globalProperties.getTaskExecutor().shutdown();
                log.info("ThreadPoolExecutor has been shut down");
            }
        };
    }

    private TelegramBotGlobalProperties.Builder createDefaultBotGlobalPropertiesBuilder(
            @NotNull RequestMappingsMatcherStrategy matcherStrategy,
            @NotNull List<BotHandlerMethodArgumentResolver> argumentResolvers,
            @NotNull List<BotHandlerMethodReturnValueHandler> returnValueHandlers,
            @NotNull TelegramConfigurationProperties properties) {
        return TelegramBotGlobalProperties.builder()
                .matcherStrategy(matcherStrategy)
                .argumentResolvers(argumentResolvers)
                .returnValueHandlers(returnValueHandlers)
                .setWebserverPort(properties.getServerPort())
                .primaryBotToken(properties.getPrimaryBotToken())
                .taskExecutor(new ThreadPoolExecutor(properties.getCorePoolSize(), properties.getMaxPoolSize(), 0L, TimeUnit.SECONDS, new SynchronousQueue<>()))
                .responseCallback(new Callback() {
                    @Override
                    public void onResponse(BaseRequest request, BaseResponse response) {
                    }

                    @Override
                    public void onFailure(BaseRequest request, IOException e) {
                    }
                });
    }

    private TelegramBotProperties.Builder createDefaultBotPropertiesBuilder(@NotNull String token, @NotNull TelegramBotGlobalProperties globalProperties) {
        return TelegramBotProperties.builder(token)
                .configure(builder -> builder
                        .apiUrl("https://api.telegram.org/bot")
                        .updateListenerSleep(environment.getProperty("telegram.bot.update-listener-sleep", Long.class, 300L))
                        .okHttpClient(new OkHttpClient.Builder()
                                .dispatcher(new Dispatcher(globalProperties.getTaskExecutor()))
                                .build()));
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        beanFactory.registerScope(TelegramScope.SCOPE,
                new TelegramScope(beanFactory, environment.getProperty("telegram.bot.session-seconds", Integer.class, 3600)));
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
