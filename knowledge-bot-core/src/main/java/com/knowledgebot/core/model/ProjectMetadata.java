package com.knowledgebot.core.model;

import java.util.Set;

public record ProjectMetadata(
    String projectPath,
    Set<String> languagesDetected,
    Set<String> frameworksDetected,
    String buildTool
) {}
