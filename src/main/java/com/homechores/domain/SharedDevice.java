package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A household device that belongs to the whole family rather than to one member — the
 * tablet in the kitchen. It is never a {@link Member}: a member is one phone, and signing a
 * member in on a second device cuts the first one off, so the family's device needs an
 * identity of its own. It can act <em>for</em> members (who did a chore is picked at the tap)
 * and never as an admin.
 *
 * <p>Holds, like a member, only the SHA-256 of the secret the device keeps in its local
 * storage; the secret is what proves the browser is this device. Not part of a backup — it
 * is a fact about a browser, not about the family — and it survives a restore.
 */
@Entity
public class SharedDevice {

    /** Devices a home may pair; a family has one or two of these, and the cap keeps a leaked
     *  PIN from planting an unbounded number of them. */
    public static final int MAX_PER_HOME = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    /** What the admin called it ("Kitchen iPad"), shown in the header and the Admin tab. */
    @Column(length = InputLimits.MEMBER_NAME)
    private String name;

    @Column(length = 64)
    private String secretHash;

    private Instant pairedAt = Instant.now();

    /** When the device last opened the board; the Admin tab shows it so a forgotten device
     *  can be told from one in daily use. */
    private Instant lastSeenAt;

    protected SharedDevice() {
    }

    public SharedDevice(String homeCode, String name, String secretHash) {
        this.homeCode = homeCode;
        this.name = name;
        this.secretHash = secretHash;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public String getName() {
        return name;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public Instant getPairedAt() {
        return pairedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
