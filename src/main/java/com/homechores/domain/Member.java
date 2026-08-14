package com.homechores.domain;

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
}
