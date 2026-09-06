package com.homechores.ui;

import com.homechores.domain.Avatars;
import com.homechores.domain.Member;
import com.homechores.service.ChoreService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Pick-an-animal dialog (Kenney CC0 set, see {@link Avatars}). Opened by tapping your own
 * leaderboard chip, and from the admin rename dialog so an adult can set a device-less
 * kid's avatar. Selection from the fixed catalog only — no uploads, nothing to moderate.
 */
class AvatarPickerDialog extends Dialog {

    AvatarPickerDialog(ChoreService service, Member member, Runnable onChanged) {
        setHeaderTitle(T.tr("avatar.pick.title", member.getName()));
        setWidth("min(92vw, 26em)");

        Div grid = new Div();
        grid.addClassName("avatar-grid");

        // "Use initials" cell first: opting out is a choice, not a buried setting.
        Div none = new Div();
        none.addClassName("avatar-cell");
        none.setText(T.tr("avatar.none"));
        if (member.getAvatar() == null) {
            none.addClassName("selected");
        }
        none.addClickListener(e -> pick(service, member, null, onChanged));
        grid.add(none);

        for (String id : Avatars.IDS) {
            Div cell = new Div();
            cell.addClassName("avatar-cell");
            Image img = new Image(Avatars.urlFor(id), id);
            cell.add(img);
            if (id.equals(member.getAvatar())) {
                cell.addClassName("selected");
            }
            cell.addClickListener(e -> pick(service, member, id, onChanged));
            grid.add(cell);
        }
        add(grid);
        getFooter().add(new Button(T.tr("common.cancel"), e -> close()));
    }

    private void pick(ChoreService service, Member member, String avatarId, Runnable onChanged) {
        service.setAvatar(member.getId(), avatarId);
        close();
        Notification n = Notification.show(T.tr("avatar.saved"), 2000,
                Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        onChanged.run();
    }
}
