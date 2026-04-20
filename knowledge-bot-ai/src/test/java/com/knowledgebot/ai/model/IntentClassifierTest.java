package com.knowledgebot.ai.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IntentClassifier}.
 * Verifies that prompts are correctly classified by complexity tier.
 */
@DisplayName("IntentClassifier")
class IntentClassifierTest {

    private IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new IntentClassifier();
    }

    @Test
    @DisplayName("Should classify summarize/explain queries as SIMPLE")
    void shouldClassifySimpleQueries() {
        assertEquals(TaskComplexity.SIMPLE, classifier.classify("Summarize this file"));
        assertEquals(TaskComplexity.SIMPLE, classifier.classify("What is a Spring Bean?"));
        assertEquals(TaskComplexity.SIMPLE, classifier.classify("Explain the @Service annotation"));
        assertEquals(TaskComplexity.SIMPLE, classifier.classify("List all classes"));
    }

    @Test
    @DisplayName("Should classify generate/create/refactor queries as MODERATE or higher")
    void shouldClassifyModerateQueries() {
        TaskComplexity result = classifier.classify("Generate a REST controller for users");
        assertNotEquals(TaskComplexity.SIMPLE, result);

        TaskComplexity refactor = classifier.classify("Refactor this service to use records");
        assertTrue(
            refactor == TaskComplexity.MODERATE || refactor == TaskComplexity.COMPLEX,
            "Refactor should be at least MODERATE"
        );
    }

    @Test
    @DisplayName("Should classify architecture/deploy queries as COMPLEX or REASONING_HEAVY")
    void shouldClassifyComplexQueries() {
        TaskComplexity result = classifier.classify("Design the architecture for a microservices pipeline");
        assertTrue(
            result == TaskComplexity.COMPLEX || result == TaskComplexity.REASONING_HEAVY,
            "Architecture query should be COMPLEX or higher"
        );

        TaskComplexity deploy = classifier.classify("Deploy the app with CI/CD on Kubernetes");
        assertTrue(
            deploy == TaskComplexity.COMPLEX || deploy == TaskComplexity.REASONING_HEAVY,
            "Deploy query should be COMPLEX or higher"
        );
    }

    @Test
    @DisplayName("Should classify debug/troubleshoot/root cause as REASONING_HEAVY")
    void shouldClassifyReasoningHeavyQueries() {
        assertEquals(TaskComplexity.REASONING_HEAVY, classifier.classify("Debug why this service fails"));
        assertEquals(TaskComplexity.REASONING_HEAVY, classifier.classify("Find the root cause of the NullPointerException"));
        assertEquals(TaskComplexity.REASONING_HEAVY, classifier.classify("Optimize performance of this query"));
    }

    @Test
    @DisplayName("Should default to MODERATE for ambiguous mid-length prompts")
    void shouldDefaultToModerateForAmbiguousPrompts() {
        // Exactly 150 chars, no keyword → defaults per logic
        String ambiguous = "a".repeat(150);
        TaskComplexity result = classifier.classify(ambiguous);
        assertEquals(TaskComplexity.MODERATE, result);
    }
}
