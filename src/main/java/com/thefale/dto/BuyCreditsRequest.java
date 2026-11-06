package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuyCreditsRequest {

    @NotBlank(message = "Package ID is required (pkg-1, pkg-2, pkg-3, pkg_10, pkg_30, pkg_100)")
    private String packageId;

    private Integer credits;
    private Long pricePaise;
}
