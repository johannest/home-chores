package com.homechores.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** An admin-defined reward: complete a chore every day for {@code days} in a row → {@code credits}. */
@Entity
public class SpreeTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeCode;

    private int days;

    private int credits;

    protected SpreeTier() {
    }

    public SpreeTier(String homeCode, int days, int credits) {
        this.homeCode = homeCode;
        this.days = days;
        this.credits = credits;
    }

    public Long getId() {
        return id;
    }

    public String getHomeCode() {
        return homeCode;
    }

    public int getDays() {
        return days;
    }

    public int getCredits() {
        return credits;
    }
}
