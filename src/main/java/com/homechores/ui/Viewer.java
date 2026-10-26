package com.homechores.ui;

import com.homechores.service.Author;

/**
 * Who a board is drawn for: a member on their own phone, or the family's shared device
 * (§4.1.3), which has a home but no member of its own. Exactly one of {@code memberId} and
 * {@code deviceId} is set. The panels keep the member id as before — null on a device, which
 * every "mine" check already reads as "not mine" — and ask {@link #isDevice()} where the two
 * screens differ: who-dependent locks, the undo strip, the picker in the confirm dialog.
 */
record Viewer(String homeCode, Long memberId, Long deviceId) {

    static Viewer member(String homeCode, Long memberId) {
        return new Viewer(homeCode, memberId, null);
    }

    static Viewer device(String homeCode, Long deviceId) {
        return new Viewer(homeCode, null, deviceId);
    }

    boolean isDevice() {
        return deviceId != null;
    }

    /** Who this screen's list edits are written as; the list service checks it against the row. */
    Author author() {
        return isDevice() ? Author.device(deviceId) : Author.member(memberId);
    }
}
