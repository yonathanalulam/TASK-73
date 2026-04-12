package com.dojostay.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A group of interchangeable rooms at a {@link Property} — e.g. "Standard
 * Queen", "Tatami Deluxe". Used by the comparison view: the client displays
 * per-type rate and occupancy, then confirms a specific room at booking time.
 * {@code baseRateCents} is the fallback nightly rate applied when a day has
 * no row in {@code nightly_rates}. {@code features} is an opaque summary
 * blob shown in the comparison card; we keep it as free text to avoid
 * modeling a second taxonomy.
 */
@Entity
@Table(name = "room_types")
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy = 1;

    @Column(name = "base_rate_cents", nullable = false)
    private int baseRateCents = 0;

    @Column(length = 1000)
    private String features;

    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getMaxOccupancy() { return maxOccupancy; }
    public void setMaxOccupancy(int maxOccupancy) { this.maxOccupancy = maxOccupancy; }
    public int getBaseRateCents() { return baseRateCents; }
    public void setBaseRateCents(int baseRateCents) { this.baseRateCents = baseRateCents; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
}
