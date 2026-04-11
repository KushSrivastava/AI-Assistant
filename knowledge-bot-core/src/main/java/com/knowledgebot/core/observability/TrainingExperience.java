package com.knowledgebot.core.observability;

import java.util.List;
import java.util.Map;

/**
 * Represents a single execution trace formatted for Supervised Fine-Tuning (SFT).
 * * @param datasetType "STANDARD_EXECUTION" or "CORRECTION_TRIUMPH"
 * @param taskId      The internal ID of the task executed
 * @param messages    The sequence of prompts and responses (System, User, Assistant)
 */
public record TrainingExperience(
        String datasetType,
        String taskId,
        List<Map<String, String>> messages
) {}