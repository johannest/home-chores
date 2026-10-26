package com.homechores.service;

/**
 * Who writes to a home's lists: a member, or the home's shared device (§4.1.3), which leaves no
 * name on a line. Exactly one of the two ids is set. {@link ListItemService} checks either one
 * against the home of the row it touches — the session proves the device's secret, but which
 * home a device or member belongs to is the database's to say, not the caller's.
 */
public record Author(Long memberId, Long sharedDeviceId) {

    public Author {
        if ((memberId == null) == (sharedDeviceId == null)) {
            throw new IllegalArgumentException("An author is a member or a shared device");
        }
    }

    public static Author member(Long memberId) {
        return new Author(memberId, null);
    }

    public static Author device(Long sharedDeviceId) {
        return new Author(null, sharedDeviceId);
    }

    public boolean isDevice() {
        return sharedDeviceId != null;
    }
}
