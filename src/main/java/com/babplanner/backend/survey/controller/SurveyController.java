package com.babplanner.backend.survey.controller;

import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.security.AuthenticatedUser;
import com.babplanner.backend.survey.dto.SurveyResponseView;
import com.babplanner.backend.survey.dto.SurveySubmitRequest;
import com.babplanner.backend.survey.service.SurveyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 만족도 조사. 두 endpoint 모두 bearerAuth가 필요하다. */
@RestController
@RequestMapping("/api/v1/surveys")
public class SurveyController {

    private final SurveyService surveyService;

    public SurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /** 활성 설문이 없으면 data가 null이다. */
    @GetMapping("/active")
    public ApiResponse<SurveyResponseView> getActive() {
        return ApiResponse.success(surveyService.getActiveSurvey());
    }

    @PostMapping("/{surveyId}/responses")
    public ResponseEntity<Void> submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long surveyId,
            @Valid @RequestBody SurveySubmitRequest request) {
        surveyService.submit(principal.id(), surveyId, request);
        // OpenAPI가 201을 정의하며 본문이 없다.
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
