package com.homechores.domain;

import java.util.List;
import java.util.Set;

/**
 * The fixed avatar catalog: Kenney's "Animal Pack Redux" round faces (CC0 1.0 — see
 * {@code META-INF/resources/avatars/LICENSE.txt}), served as static PNGs. A member's
 * {@code avatar} column holds one of these ids or null (null = the initials+color dot).
 *
 * <p>A whitelist in the {@link InputLimits} spirit: ids arrive from the browser and from
 * hand-editable backup files, so everything is validated against this list — never
 * interpolated into a URL unchecked.
 */
public final class Avatars {

    /** Stable ids; each maps to {@code /avatars/<id>.png}. Order = picker grid order. */
    public static final List<String> IDS = List.of(
            "bear", "buffalo", "chick", "chicken", "cow", "crocodile", "dog", "duck",
            "elephant", "frog", "giraffe", "goat", "gorilla", "hippo", "horse", "monkey",
            "moose", "narwhal", "owl", "panda", "parrot", "penguin", "pig", "rabbit",
            "rhino", "sloth", "snake", "walrus", "whale", "zebra");

    private static final Set<String> VALID = Set.copyOf(IDS);

    private Avatars() {
    }

    public static boolean isValid(String id) {
        return id != null && VALID.contains(id);
    }

    /** The static-resource URL for a valid id; null for anything else. */
    public static String urlFor(String id) {
        return isValid(id) ? "avatars/" + id + ".png" : null;
    }

    /** Whitelisted id or null — for user input and restored backups alike. */
    public static String sanitize(String id) {
        return isValid(id) ? id : null;
    }
}
