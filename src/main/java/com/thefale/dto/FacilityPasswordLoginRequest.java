package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityPasswordLoginRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^(\\+91)?[6-9][0-9]{9}$", message = "Enter a valid Indian mobile number")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(max = 100, message = "Password is too long")
    private String password;
}
