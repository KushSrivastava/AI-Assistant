package com.knowledgebot.core.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TokenBudgetService}.
 * Tests token estimation, budget tracking, and circuit-breaker behaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenBudgetService")
class TokenBudgetServiceTest {

    @Mock
    private HardwareMetricsService hardwareMetrics;

    private TokenBudgetService tokenBudgetService;

    @BeforeEach
    void setUp() {
        when(hardwareMetrics.isRamCritical()).thenReturn(false);
        tokenBudgetService = new TokenBudgetService(hardwareMetrics);
        tokenBudgetService.resetSession();
    }

    @Test
    @DisplayName("Should estimate tokens approximately at 1 per 3.5 chars")
    void shouldEstimateTokens() {
        // 350 chars ≈ 100 tokens
        String text = "a".repeat(350);
        long tokens = tokenBudgetService.estimateTokens(text);
        assertEquals(100L, tokens, "Expected 350 chars / 3.5 = 100 tokens");
    }

    @Test
    @DisplayName("Should return 0 tokens for null or empty text")
    void shouldReturnZeroForEmpty() {
        assertEquals(0L, tokenBudgetService.estimateTokens(""));
        assertEquals(0L, tokenBudgetService.estimateTokens(null));
    }

    @Test
    @DisplayName("Should detect text within budget")
    void shouldAllowTextWithinBudget() {
        String smallText = "Hello world"; // ~3 tokens
        assertTrue(tokenBudgetService.isWithinBudget(smallText, 100));
    }

    @Test
    @DisplayName("Should reject text exceeding budget")
    void shouldRejectTextExceedingBudget() {
        String bigText = "a".repeat(1000); // ~285 tokens
        assertFalse(tokenBudgetService.isWithinBudget(bigText, 10));
    }

    @Test
    @DisplayName("Should open circuit breaker when budget is exceeded")
    void shouldOpenCircuitBreakerWhenBudgetExceeded() {
        tokenBudgetService.setSessionBudget(50);
        assertFalse(tokenBudgetService.isCircuitOpen());

        tokenBudgetService.addTokens(100); // exceeds budget of 50
        assertTrue(tokenBudgetService.isCircuitOpen());
    }

    @Test
    @DisplayName("Should throw exception when assertBudgetAvailable is called on open circuit")
    void shouldThrowWhenCircuitOpen() {
        tokenBudgetService.setSessionBudget(10);
        tokenBudgetService.addTokens(100);

        assertThrows(
            TokenBudgetService.TokenBudgetExceededException.class,
            () -> tokenBudgetService.assertBudgetAvailable()
        );
    }

    @Test
    @DisplayName("Should reset circuit breaker on resetSession")
    void shouldResetOnSessionReset() {
        tokenBudgetService.setSessionBudget(10);
        tokenBudgetService.addTokens(100);
        assertTrue(tokenBudgetService.isCircuitOpen());

        tokenBudgetService.resetSession();
        assertFalse(tokenBudgetService.isCircuitOpen());
        assertEquals(0L, tokenBudgetService.getSessionTokens());
    }
}
