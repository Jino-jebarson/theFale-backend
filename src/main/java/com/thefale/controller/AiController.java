package com.thefale.controller;

import com.thefale.dto.AiRequestDto;
import com.thefale.dto.AiResponseDto;
import com.thefale.service.ai.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Engine", description = "Endpoints powering the 20 FALE AI features via Gemini / Claude / Clinical Engine")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute any of the 20 FALE AI tasks")
    public ResponseEntity<AiResponseDto> executeTask(@RequestBody AiRequestDto request) {
        AiResponseDto response = aiService.executeTask(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @Operation(summary = "Get current AI engine provider status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "activeProvider", aiService.getActiveProvider(),
                "status", "ONLINE",
                "supportedTasksCount", 20
        ));
    }
}

