package com.homechores;

import com.vaadin.flow.spring.RootMappedCondition;
import com.vaadin.flow.spring.SpringBootAutoConfiguration;
import com.vaadin.flow.spring.SpringServlet;
import com.vaadin.flow.spring.VaadinConfigurationProperties;
import jakarta.servlet.MultipartConfigElement;
import org.atmosphere.cpr.ApplicationConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.WebApplicationContext;

/**
 * Caps the thread pools Atmosphere uses for server push.
 *
 * <p>The host counts threads against a 100-process limit (see the README's thread budget).
 * Vaadin's push setup leaves Atmosphere's pools at their defaults: an unbounded message
 * dispatcher and up to 200 async-write threads, both platform threads that come and go
 * with a 30 s idle timeout. Every {@code ui.push()} is a task on them, so one change
 * fanning out to many attached boards, or many homes changing at once, is exactly the
 * burst that would blow the budget — and exactly what more users bring. The pools are
 * sized here, as servlet init parameters, which Vaadin's Atmosphere bootstrap honours.
 *
 * <p>The bean replaces the one from {@link SpringBootAutoConfiguration}, which steps aside
 * when a {@code ServletRegistrationBean<SpringServlet>} already exists, and is built with
 * the same helper so the url mapping, push mapping and multipart setup stay Vaadin's.
 */
@Configuration(proxyBeanMethods = false)
class PushThreadBudget {

    /**
     * @param dispatchThreads
     *            threads that pick push messages off the broadcaster queue. Must be at
     *            least 2: Atmosphere turns 1 back into an unbounded pool.
     * @param writeThreads
     *            threads that write push messages to the long-polling connections. A slow
     *            phone holds one for the length of the write, so leave a little headroom.
     */
    @Bean
    ServletRegistrationBean<SpringServlet> vaadinServletRegistration(
            WebApplicationContext context,
            ObjectProvider<MultipartConfigElement> multipartConfig,
            VaadinConfigurationProperties properties,
            @Value("${homechores.atmosphere.dispatch-threads:4}") int dispatchThreads,
            @Value("${homechores.atmosphere.write-threads:8}") int writeThreads) {
        boolean rootMapping = RootMappedCondition.isRootMapping(properties.getUrlMapping());
        ServletRegistrationBean<SpringServlet> registration =
                SpringBootAutoConfiguration.configureServletRegistrationBean(
                        multipartConfig, properties, new SpringServlet(context, rootMapping));
        registration.addInitParameter(
                ApplicationConfig.BROADCASTER_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE,
                String.valueOf(dispatchThreads));
        registration.addInitParameter(
                ApplicationConfig.BROADCASTER_ASYNC_WRITE_THREADPOOL_MAXSIZE,
                String.valueOf(writeThreads));
        return registration;
    }
}
