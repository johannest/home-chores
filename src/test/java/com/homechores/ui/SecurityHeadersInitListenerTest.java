package com.homechores.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The response headers every request carries. Set by the app rather than only at the proxy,
 * so they hold even if the app is ever reached without the proxy in front — which is also
 * why they are pinned here and not in a deployment check.
 */
class SecurityHeadersInitListenerTest {

    private RequestHandler theHandler() {
        ServiceInitEvent event = new ServiceInitEvent(mock(VaadinService.class));
        new SecurityHeadersInitListener().serviceInit(event);
        List<RequestHandler> added = event.getAddedRequestHandlers().toList();
        assertEquals(1, added.size(), "one handler, headers only");
        return added.get(0);
    }

    @Test
    void everyResponse_carriesTheFourHeaders_andTheRequestGoesOnToTheNormalChain()
            throws IOException {
        VaadinResponse response = mock(VaadinResponse.class);

        boolean handled = theHandler().handleRequest(mock(VaadinSession.class),
                mock(VaadinRequest.class), response);

        assertFalse(handled, "headers only: the normal handler chain still serves the request");
        verify(response).setHeader("X-Frame-Options", "DENY");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        verify(response).setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        verifyNoMoreInteractions(response);
    }
}
