package com.thefale.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class TopUpReviewRequest {
    private boolean approved;
    @NotBlank @Pattern(regexp="^[A-Za-z0-9]{8,30}$") private String bankReference;
    @NotBlank @Size(min=10, max=1000) private String notes;
}
