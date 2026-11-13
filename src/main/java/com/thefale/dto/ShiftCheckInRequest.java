package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftCheckInRequest {
    private String pin;
    private String qrToken;
    private String method;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private java.time.Instant locationCapturedAt;
    private java.time.Instant selfieCapturedAt;
    private String selfieDataUrl;
}

