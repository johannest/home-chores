package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;

/** A person belonging to a {@link Home}. */
@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The home code this member belongs to. */
    private String homeCode;

    /** Length backstop for {@link InputLimits#MEMBER_NAME}; the service layer clips first.
     *  ({@code ddl-auto=update} won't narrow the column on databases that predate it.) */
    @Column(length = InputLimits.MEMBER_NAME)
    private String name;

    /** Color used for this member's avatar dot (CSS color string). */
    @Column(length = 32)
    private String color;

    /** Chosen avatar id from the fixed {@link Avatars} catalog; null = initials dot. */
    @Column(length = 32)
    private String avatar;

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

    /**
     * When this member (or the adult acting for them) ticked the user-agreement checkbox —
     * every create/join form gates on it, so member creation implies consent. Nullable:
     * members that predate the agreement read as null and are grandfathered. Deliberately
     * excluded from backup export; it is a server-side audit fact, like the device hash.
     */
    private Instant termsAcceptedAt;

    // ---- Chore reminder (Web Push) — all nullable: null reminderTime = reminders off ----

    /** Wall-clock reminder time "HH:mm" in the member's own timezone (see zoneId). */
    @Column(length = 5)
    private String reminderTime;

    /**
     * The member's IANA timezone id, refreshed from the browser every time they open the
     * board. Persisted because the reminder sweep runs with no session to ask: "19:00"
     * must mean 19:00 on the member's own clock.
     */
    @Column(length = 50)
    private String zoneId;

    /** Language tag for the reminder text, stamped when the reminder is saved. */
    @Column(length = 5)
    private String reminderLocale;

    /**
     * The member-local date a reminder was last considered for (sent or suppressed).
     * Makes the sweep idempotent across restarts and missed minutes: fire when local time
     * has passed reminderTime AND this isn't today yet.
     */
    private LocalDate lastRemindedOn;

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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
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

    public String getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(String reminderTime) {
        this.reminderTime = reminderTime;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getReminderLocale() {
        return reminderLocale;
    }

    public void setReminderLocale(String reminderLocale) {
        this.reminderLocale = reminderLocale;
    }

    public LocalDate getLastRemindedOn() {
        return lastRemindedOn;
    }

    public void setLastRemindedOn(LocalDate lastRemindedOn) {
        this.lastRemindedOn = lastRemindedOn;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(Instant termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public String getDeviceSecretHash() {
        return deviceSecretHash;
    }

    public void setDeviceSecretHash(String deviceSecretHash) {
        this.deviceSecretHash = deviceSecretHash;
    }
}
