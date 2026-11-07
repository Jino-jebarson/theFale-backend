package com.thefale.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateViewStatsDto {
    private long totalViews;
    private long viewsToday;
    private long hospitalsViewedToday;
    private int weeklyGrowthPercent;
    private boolean hasIncreased;
}

