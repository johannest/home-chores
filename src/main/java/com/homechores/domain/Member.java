package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A person belonging to a {@link Home}. */
@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The home code this member belongs to. */
    private String homeCode;

    private String name;

    /** Color used for this member's avatar dot (CSS color string). */
    private String color;

    /** Whether this member has admin rights. */
    private boolean admin = false;

    private Instant joinedAt = Instant.now();

    /**
     * When this row was created by the running server. Distinct from {@link #joinedAt},
     * which is the business "member since" date and can be back-dated by a backup restore.
     * This one is stamped by the constructor and never copied from a backup, so a member
     * minted since the app started (a fresh join, or one recreated by a restore) always has
     * it set, while rows that predate the column read back null. The legacy-identity
     * migration keys on that to tell genuine pre-upgrade devices from new arrivals.
     */
    private Instant createdAt = Instant.now();

    /**
     * SHA-256 of the secret this member's device holds in its local storage. A member id is
     * a small sequential number anyone can guess; the secret is what actually proves "this
     * browser is that member". Only the hash is kept, so the database (and its backups)
     * can't impersonate a device. Null until the member's first sign-in issues one.
     */
    @Column(length = 64)
    private String deviceSecretHash;

    protected Member() {
    }

    public Member(String homeCode, String name, String color, boolean admin) {
        this.homeCode = homeCode;
        this.name = name;
        this.color = color;
        this.admin = admin;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeviceSecretHash() {
        return deviceSecretHash;
    }

    public void setDeviceSecretHash(String deviceSecretHash) {
        this.deviceSecretHash = deviceSecretHash;
    }
}
