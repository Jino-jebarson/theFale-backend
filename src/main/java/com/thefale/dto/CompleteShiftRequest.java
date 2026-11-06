package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteShiftRequest {
    private String panNumber;
    private Boolean tdsConsent;
    private String declarationNotes;
}

