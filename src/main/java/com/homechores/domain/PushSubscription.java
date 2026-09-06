package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * One browser's Web Push subscription for chore reminders. Per device, not per member: a
 * member who signs in on a new phone subscribes again there, and the push service address
 * ({@code endpoint}) plus the encryption keys ({@code p256dh}, {@code auth}) are only
 * valid for the browser that minted them.
 *
 * <p>These are device credentials in the same sense as {@code Member.deviceSecretHash}:
 * whoever holds them can send notifications to that device. They are therefore never
 * exported in backups, and are deleted with their member and home.
 */
@Entity
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String homeCode;

    /** The push service URL for this browser; unique — re-subscribing upserts. */
    @Column(length = 1024, unique = true)
    private String endpoint;

    /** Client public key (base64url) for payload encryption (RFC 8291). */
    @Column(length = 255)
    private String p256dh;

    /** Client auth secret (base64url) for payload encryption (RFC 8291). */
    @Column(length = 64)
    private String auth;

    private Instant createdAt = Instant.now();

    protected PushSubscription() {
    }

    public PushSubscription(Long memberId, String homeCode, String endpoint,
                            String p256dh, String auth) {
        this.memberId = memberId;
        this.homeCode = homeCode;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
