package com.dojostay.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Per-day override of a {@link RoomType}'s rate and availability. A missing
 * row means "use the base rate from room_types and assume inventory is
 * unconstrained" — the upsert path writes a row when an operator overrides
 * either the rate or the available count for a specific night.
 *
 * <p>Stored in cents (integer) so arithmetic stays exact — we never use
 * floating point for money.
 */
@Entity
@Table(name = "nightly_rates")
public class NightlyRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(name = "rate_cents", nullable = false)
    private int rateCents;

    @Column(name = "available_count", nullable = false)
    private int availableCount;

    public Long getId() { return id; }
    public Long getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Long roomTypeId) { this.roomTypeId = roomTypeId; }
    public LocalDate getStayDate() { return stayDate; }
    public void setStayDate(LocalDate stayDate) { this.stayDate = stayDate; }
    public int getRateCents() { return rateCents; }
    public void setRateCents(int rateCents) { this.rateCents = rateCents; }
    public int getAvailableCount() { return availableCount; }
    public void setAvailableCount(int availableCount) { this.availableCount = availableCount; }
}
