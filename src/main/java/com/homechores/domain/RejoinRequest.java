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
 * A device asking to sign back in as an existing {@link Member} — raised when a family
 * member clears their browser storage and loses the identity kept there.
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

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public Long getMemberId() {
        return memberId;
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
