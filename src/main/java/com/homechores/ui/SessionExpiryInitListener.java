package com.homechores.ui;

import com.vaadin.flow.server.CustomizedSystemMessages;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Makes an expired session invisible to the family.
 *
 * <p>Sessions here are deliberately short (see {@link SessionContext}), so members will hit
 * expiry routinely — a phone put down for a few minutes is the normal case, not an error.
 * Vaadin's default response is a modal "Session Expired — take note of any unsaved data"
 * notice, which is the right message for a banking app and quite wrong for a chore board.
 *
 * <p>Instead the browser is sent to the landing page, which reads the device's stored
 * identity and puts it straight back on the board ({@code LandingView.restoreFrom}). The
 * user sees a reload, not a dialog, and nothing is lost because nothing was half-typed:
 * chores are taps.
 */
@Component
public class SessionExpiryInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().setSystemMessagesProvider(info -> {
            CustomizedSystemMessages messages = new CustomizedSystemMessages();
            messages.setSessionExpiredNotificationEnabled(false);
            messages.setSessionExpiredURL("/");
            return messages;
        });
    }
}
