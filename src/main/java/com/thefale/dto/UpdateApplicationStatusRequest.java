package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApplicationStatusRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(SHORTLISTED|INTERVIEW|INTERVIEW_SCHEDULED|OFFER|OFFERED|HIRED|CONFIRMED|CONFIRM_SHIFT|CLAIMED|REJECTED|TIMESHEET_SUBMITTED|TIMESHEET_VERIFIED|COMPLETED)$", message = "Invalid status transition")
    private String status;

    private Instant interviewScheduledAt;
    private String notes;
    private Integer actualHours;
}
