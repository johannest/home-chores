package com.homechores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.ServletContext;
import java.util.Collections;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.ApplicationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * On a real servlet container Vaadin bootstraps Atmosphere at context start and parks the
 * framework in the servlet context. This checks that the pool caps reached it, not just the
 * registration bean.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class PushThreadBudgetRuntimeTest {

    @Autowired
    private ServletContext servletContext;

    @Test
    void atmosphereSeesTheCaps() {
        AtmosphereFramework framework = Collections.list(servletContext.getAttributeNames()).stream()
                .map(servletContext::getAttribute)
                .filter(AtmosphereFramework.class::isInstance)
                .map(AtmosphereFramework.class::cast)
                .findFirst().orElse(null);
        assertNotNull(framework, "Vaadin's websocket initializer should have pre-initialized Atmosphere");
        var config = framework.getAtmosphereConfig();
        assertEquals("4", config.getInitParameter(ApplicationConfig.BROADCASTER_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE));
        assertEquals("8", config.getInitParameter(ApplicationConfig.BROADCASTER_ASYNC_WRITE_THREADPOOL_MAXSIZE));
    }
}
