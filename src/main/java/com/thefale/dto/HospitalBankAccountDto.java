package com.thefale.dto;

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
public class HospitalBankAccountDto {
    private UUID id;
    private String bankName;
    private String accountNumberMasked;
    private String accountNumberLast4;
    private String ifsc;
    private String branch;
    private String accountHolderName;
    private String accountType;
    private String status;
    private Instant verifiedAt;
}
