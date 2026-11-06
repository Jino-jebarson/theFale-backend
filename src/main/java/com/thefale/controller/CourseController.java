package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.CourseDto;
import com.thefale.dto.CourseProgressUpdateRequest;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "CME Academy", description = "Accredited CME courses, enrollment, module progress, and completion certificates")
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(summary = "Get list of available CME courses with personalized enrollment progress")
    public ResponseEntity<ApiResponse<List<CourseDto>>> getCourses(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<CourseDto> courses = courseService.getCourses(principal);
        return ResponseEntity.ok(ApiResponse.success("Courses fetched successfully", courses));
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasRole('CANDIDATE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Enroll in a CME course")
    public ResponseEntity<ApiResponse<CourseDto>> enroll(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String id
    ) {
        CourseDto course = courseService.enroll(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Enrolled in course successfully", course));
    }

    @PostMapping("/{id}/progress")
    @PreAuthorize("hasRole('CANDIDATE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update course progress percentage (issues certificate upon 100% completion)")
    public ResponseEntity<ApiResponse<CourseDto>> updateProgress(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody CourseProgressUpdateRequest request
    ) {
        CourseDto course = courseService.updateProgress(principal, id, request.getProgressPercent());
        return ResponseEntity.ok(ApiResponse.success("Course progress updated", course));
    }
}
