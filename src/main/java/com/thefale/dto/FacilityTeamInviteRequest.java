package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FacilityTeamInviteRequest {
    @NotBlank(message = "Team member name is required")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String name;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^(?:\\+?91[ -]?)?[6-9]\\d{9}$", message = "Enter a valid Indian mobile number")
    private String phone;
}
