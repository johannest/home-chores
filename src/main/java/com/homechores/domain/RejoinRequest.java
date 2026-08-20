package com.homechores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * A device asking for an admin's approval to enter a home — either to sign back in as an
 * existing {@link Member} (a family member cleared their browser storage and lost the
 * identity kept there), or to join for the first time.
 *
 * <p>The two flavours are told apart by {@link #isJoin()}: a first-time join carries the
 * {@code requestedName} typed on the landing page and no {@code memberId} yet — the member
 * is only created when an admin approves, at which point {@code memberId} is filled in so
 * the waiting device can sign in as it.
 *
 * <p>The {@code deviceToken} is a random secret handed to the requesting browser and kept
 * in its local storage. It is what lets that specific device (and only it) pick the
 * approval up later, even after a reload.
 */
@Entity
public class RejoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    private Long memberId;

    /** The nickname a first-time joiner asked for; null for sign-back-in requests. */
    @Column(length = 60)
    private String requestedName;

    /** Random secret identifying the requesting browser. */
    @Column(length = 64, unique = true)
    private String deviceToken;

    @Enumerated(EnumType.STRING)
    private RejoinStatus status = RejoinStatus.PENDING;

    private Instant requestedAt = Instant.now();

    private Instant decidedAt;

    private Long decidedByMemberId;

    protected RejoinRequest() {
    }

    public RejoinRequest(String homeCode, Long memberId, String deviceToken) {
        this.homeCode = homeCode;
        this.memberId = memberId;
        this.deviceToken = deviceToken;
    }

    /** A first-time join request: no member yet, just the name the joiner asked for. */
    public static RejoinRequest joinRequest(String homeCode, String requestedName,
                                            String deviceToken) {
        RejoinRequest r = new RejoinRequest(homeCode, null, deviceToken);
        r.requestedName = requestedName;
        return r;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getRequestedName() {
        return requestedName;
    }

    /** Whether this is a first-time join rather than an existing member signing back in. */
    public boolean isJoin() {
        return requestedName != null;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public RejoinStatus getStatus() {
        return status;
    }

    public void setStatus(RejoinStatus status) {
        this.status = status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Long getDecidedByMemberId() {
        return decidedByMemberId;
    }

    public void setDecidedByMemberId(Long decidedByMemberId) {
        this.decidedByMemberId = decidedByMemberId;
    }
}
