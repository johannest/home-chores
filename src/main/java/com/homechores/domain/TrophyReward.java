package com.homechores.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * An admin-set 💎 reward for reaching a {@link Trophy} rung. One row per home and rung, only
 * for rungs that pay something: a home that never sets one has no rows and trophies pay nothing.
 */
@Entity
public class TrophyReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    private int rung;

    private int credits;

    protected TrophyReward() {
    }

    public TrophyReward(String homeCode, int rung, int credits) {
        this.homeCode = homeCode;
        this.rung = rung;
        this.credits = credits;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public int getRung() {
        return rung;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }
}
