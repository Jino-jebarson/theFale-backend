package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocumShiftDto {
    private UUID id;
    private UUID facilityId;
    private String facilityName;
    private String city;
    private String title;
    private String department;
    private Instant startAt;
    private Instant endAt;
    private int hours;
    private long ratePerHourPaise;
    private long surgeRatePerHourPaise;
    private String surgeLabel;
    private long totalPayPaise;
    private int openings;
    private int slotsClaimed;

    @JsonProperty("urgent")
    @JsonAlias({"urgent", "isUrgent"})
    private boolean urgent;

    private String status;

    @JsonProperty("hasClaimed")
    private boolean hasClaimed;
}
