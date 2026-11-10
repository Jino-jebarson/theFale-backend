package com.thefale.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminForgotPasswordRequest {

    @NotBlank(message = "Work email is required")
    @Email(message = "Please provide a valid email address")
    private String workingmailid;

    @NotBlank(message = "Platform passcode is required")
    private String passcode;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 100, message = "New password must be at least 6 characters")
    private String newPassword;
}

