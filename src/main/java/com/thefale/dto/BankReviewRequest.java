package com.thefale.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class BankReviewRequest {
    private boolean approved;
    @NotNull private java.time.Instant expectedUpdatedAt;
    @NotBlank @Size(min=10, max=1000) private String notes;
}
