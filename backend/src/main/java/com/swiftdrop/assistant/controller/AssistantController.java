package com.swiftdrop.assistant.controller;

import com.swiftdrop.assistant.config.AssistantProperties;
import com.swiftdrop.assistant.dto.request.ChatRequest;
import com.swiftdrop.assistant.dto.request.FeeEstimateRequest;
import com.swiftdrop.assistant.dto.response.ChatResponse;
import com.swiftdrop.assistant.dto.response.FeeEstimateCardsResponse;
import com.swiftdrop.assistant.service.AssistantService;
import com.swiftdrop.assistant.tools.ToolContext;
import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Assistant", description = "The AI delivery assistant for signed-in customers")
@RestController
@RequestMapping(AssistantController.PATH)
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class AssistantController {

    static final String PATH = ApiPaths.API_V1 + "/assistant";
    static final String RATE_LIMIT_KEY_PREFIX = "assistant:customer:";

    private final AssistantService assistantService;
    private final RateLimiter rateLimiter;
    private final AssistantProperties properties;

    @Operation(summary = "Ask the assistant",
            description = "Send the conversation so far (user and assistant text only); the reply holds cards built "
                    + "from tool results. mode RULES means basic mode.")
    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal AuthenticatedUser customer,
                             @Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        checkRateLimit(customer);
        return assistantService.chat(request, context(customer, http));
    }

    @Operation(summary = "Estimate a fee from the chat's fee form", description = "No AI is used.")
    @PostMapping("/fee-estimate")
    public FeeEstimateCardsResponse estimateFee(@AuthenticationPrincipal AuthenticatedUser customer,
                                                @Valid @RequestBody FeeEstimateRequest request,
                                                HttpServletRequest http) {
        checkRateLimit(customer);
        return assistantService.estimateFee(request, context(customer, http));
    }

    private void checkRateLimit(AuthenticatedUser customer) {
        var decision = rateLimiter.hit(RATE_LIMIT_KEY_PREFIX + customer.userId(), properties.rateLimit());
        if (!decision.allowed()) {
            throw new RateLimitExceededException("Too many questions to the assistant.", decision.retryAfter());
        }
    }

    private static ToolContext context(AuthenticatedUser customer, HttpServletRequest http) {
        return new ToolContext(UUID.fromString(customer.userId()), http.getRemoteAddr());
    }
}
