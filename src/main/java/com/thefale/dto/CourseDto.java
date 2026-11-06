package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDto {

    private String id;
    private String title;
    private String category;
    private String duration;
    private int cmePoints;
    private String summary;

    @JsonProperty("forStudents")
    private boolean forStudents;

    @JsonProperty("enrolled")
    private boolean enrolled;

    private int progressPercent;

    @JsonProperty("completed")
    private boolean completed;

    private Instant completedAt;
    private String certificateRef;
}
