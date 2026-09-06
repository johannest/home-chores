package com.homechores.ui;

import com.homechores.domain.Avatars;
import com.homechores.domain.Member;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;

/**
 * The one way a member's face is drawn: a colored circle holding either their chosen
 * animal avatar (see {@link Avatars}) or their initials. Used by the leaderboard chips
 * and the admin member list, which previously each rolled their own dot.
 */
final class MemberAvatar {

    private MemberAvatar() {
    }

    /** The circle: member color as background, avatar image or initials inside. */
    static Div dot(Member m) {
        Div dot = new Div();
        dot.addClassName("avatar-dot");
        dot.getStyle().set("background", m.getColor());
        String url = Avatars.urlFor(m.getAvatar());
        if (url != null) {
            Image img = new Image(url, m.getName());
            dot.add(img);
        } else {
            dot.setText(initials(m.getName()));
        }
        return dot;
    }

    /** Two initials for two-word names, otherwise the first two characters. */
    static String initials(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase();
    }
}
