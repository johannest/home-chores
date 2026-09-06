package com.homechores.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * What counts as a usable Web Push endpoint.
 *
 * <p>A push endpoint is the one URL in this application that the <em>browser</em> chooses
 * and the <em>server</em> then makes requests to. Everything else the server fetches is a
 * constant. That makes an unchecked endpoint a server-side request forgery primitive:
 * anyone who can sign in (or simply edit the value the subscribe script returns) could
 * store {@code https://127.0.0.1:9200/…} or an address on the host's private network and
 * have the reminder sweep issue a request there every day, from inside the perimeter,
 * with no response ever shown to them but plenty of signal in timing and pruning.
 *
 * <p>Two layers, deliberately different in cost and in consequence:
 * <ul>
 *   <li>{@link #isWellFormed} is pure string work — the gate at subscribe time, where a
 *       DNS lookup on the UI thread would be a poor trade and a wrong answer is
 *       permanent. A value that fails here is never a real push service and is refused
 *       outright.
 *   <li>{@link #resolvesToPublicAddress} does the lookup, and belongs on the sending path
 *       where a socket is about to be opened anyway. It is what catches the case names
 *       alone cannot: a perfectly ordinary hostname that resolves into private space.
 * </ul>
 *
 * <p>No allowlist of known push services. FCM, Mozilla, Apple and WNS are today's list;
 * a family on tomorrow's browser would silently lose their reminders, which is a worse
 * failure than the one being prevented. Blocking the destinations that must never be
 * reached is the check that stays correct as browsers change.
 */
public final class PushEndpoints {

    /** Column width, and far above any real endpoint. */
    static final int MAX_LENGTH = 1024;

    /** Suffixes that name something on the local network, never a public push service. */
    private static final Set<String> LOCAL_SUFFIXES =
            Set.of(".local", ".localhost", ".internal", ".intranet", ".lan", ".home.arpa");

    private PushEndpoints() {
    }

    /**
     * Whether this could be a push endpoint at all, judged on the string alone: HTTPS, no
     * embedded credentials, and a public-looking DNS name rather than a bare IP address or
     * an unqualified/local host name.
     *
     * <p>Real push services are always HTTPS names under a registrable domain, so the
     * shape is a genuine discriminator rather than a guess.
     */
    public static boolean isWellFormed(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (Exception e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
            return false; // plaintext, or credentials smuggled into the authority
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            return false; // IPv6 literal
        }
        if (host.equals("localhost") || LOCAL_SUFFIXES.stream().anyMatch(host::endsWith)) {
            return false;
        }
        int lastDot = host.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == host.length() - 1) {
            return false; // unqualified name ("ok", "elasticsearch") — never a push service
        }
        // A trailing all-numeric label means an IPv4 literal; push services use names.
        return !host.substring(lastDot + 1).chars().allMatch(Character::isDigit);
    }

    /**
     * Whether every address this endpoint's host resolves to is outside the ranges a push
     * service can never legitimately live in. Unresolvable hosts answer {@code false}: an
     * endpoint that cannot be verified is not one to open a connection to.
     *
     * <p>Checked immediately before sending rather than at subscribe time, so a name that
     * pointed somewhere harmless when it was stored cannot be re-pointed inwards later.
     */
    public static boolean resolvesToPublicAddress(String endpoint) {
        if (!isWellFormed(endpoint)) {
            return false;
        }
        try {
            String host = URI.create(endpoint).getHost();
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (isPrivate(address)) {
                    return false; // one bad answer is enough; round-robin must not help
                }
            }
            return true;
        } catch (UnknownHostException | IllegalArgumentException | SecurityException e) {
            return false;
        }
    }

    /** Addresses that belong to the host or its network rather than the public internet. */
    private static boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            // Carrier-grade NAT (100.64/10) and IETF protocol assignments (192.0.0/24) are
            // not covered by the java.net predicates above but are just as much "not the
            // public internet"; 169.254/16 is, but is repeated here for the metadata case.
            return (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0)
                    || (first == 169 && second == 254);
        }
        return false;
    }
}
