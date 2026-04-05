package com.knowledgebot.core.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LegacyCodeAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(LegacyCodeAnalyzer.class);

    public String analyzeForModernization(Path file) {
        try {
            String code = Files.readString(file);
            if (!file.toString().endsWith(".java")) {
                return "Not a Java file.";
            }
            CompilationUnit cu = StaticJavaParser.parse(code);
            // In a full implementation, we traverse the AST looking for ThreadLocal, old switches, etc.
            // For now, we return the parsed structure as context for the LLM.
            return String.format("Analyzed AST for classes: %s", cu.getTypes().toString());
        } catch (IOException e) {
            log.error("Failed to read file for analysis", e);
            return "Error reading file.";
        }
    }
}
