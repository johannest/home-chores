package com.homechores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Cloudflare edge ranges Tomcat skips when it walks {@code X-Forwarded-For} (README,
 * "Behind Cloudflare"). The value is a regex in the production {@code application.properties},
 * where a single backslash is an escape and vanishes on loading — so this reads the file the
 * way Tomcat will see it (the test tree has its own properties file, so a Spring placeholder
 * would not reach it) and checks it against an edge, a visitor and the loopback proxy.
 */
class TrustedProxiesTest {

    private static String trustedProxies() throws IOException {
        Properties production = new Properties();
        try (Reader in = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            production.load(in);
        }
        return production.getProperty("server.tomcat.remoteip.trusted-proxies");
    }

    @Test
    void cloudflareEdges_areTrusted_andNothingElseIs() throws IOException {
        Pattern edges = Pattern.compile(trustedProxies());
        for (String edge : new String[] {"104.16.0.1", "172.71.255.254", "162.158.0.7",
                "173.245.63.9", "198.41.255.255", "131.0.75.1"}) {
            assertTrue(edges.matcher(edge).matches(), edge + " is a Cloudflare edge");
        }
        for (String other : new String[] {"127.0.0.1", "8.8.8.8", "104.15.255.255", "104.28.0.0",
                "172.63.255.255", "172.72.0.0", "131.0.76.0", "1.2.3.4"}) {
            assertFalse(edges.matcher(other).matches(), other + " is not");
        }
    }
}
