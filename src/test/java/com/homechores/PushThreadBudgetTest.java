package com.homechores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.flow.spring.SpringServlet;
import org.atmosphere.cpr.ApplicationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;

/** The Vaadin servlet registration carries the Atmosphere pool caps and nothing else changes. */
@SpringBootTest
class PushThreadBudgetTest {

    @Autowired
    private ApplicationContext context;

    @SuppressWarnings("unchecked")
    private ServletRegistrationBean<SpringServlet> registration() {
        var vaadinRegistrations = context.getBeansOfType(ServletRegistrationBean.class).values().stream()
                .filter(r -> r.getServlet() instanceof SpringServlet)
                .toList();
        assertEquals(1, vaadinRegistrations.size(),
                "exactly one Vaadin servlet registration: ours, with the auto-configured one stepping aside");
        return (ServletRegistrationBean<SpringServlet>) vaadinRegistrations.get(0);
    }

    @Test
    void capsBothAtmospherePools() {
        var params = registration().getInitParameters();
        assertEquals("4", params.get(ApplicationConfig.BROADCASTER_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE));
        assertEquals("8", params.get(ApplicationConfig.BROADCASTER_ASYNC_WRITE_THREADPOOL_MAXSIZE));
    }

    @Test
    void keepsVaadinsOwnMappings() {
        var registration = registration();
        assertEquals("/VAADIN/push", registration.getInitParameters().get(ApplicationConfig.JSR356_MAPPING_PATH));
        assertEquals(1, registration.getUrlMappings().size());
        assertEquals("/vaadinServlet/*", registration.getUrlMappings().iterator().next(),
                "root mapping goes through the dispatcher, as with the auto-configured bean");
    }
}
