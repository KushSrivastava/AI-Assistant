package com.knowledgebot.core.scanner;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LSP-style semantic analysis for Java source files using JavaParser.
 *
 * Instead of indexing raw file text (which wastes context tokens on whitespace,
 * comments, and boilerplate), this service extracts:
 *   1. Class/interface declaration with Javadoc
 *   2. Field declarations with types
 *   3. Method signatures (not bodies) with Javadoc
 *   4. Import graph (which packages this file depends on)
 *   5. Outbound call graph (method calls made within this file)
 *
 * The resulting structured summary is much denser than raw text, dramatically
 * improving vector embedding quality for code Q&A.
 *
 * Inspired by OpenCode's LSP integration.
 */
@Service
public class SemanticIndexingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticIndexingService.class);

    private final JavaParser javaParser = new JavaParser();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a dense semantic summary of a Java source file suitable for embedding.
     * Falls back to raw file content if parsing fails.
     */
    public String buildSemanticSummary(Path javaFile) throws IOException {
        String source = Files.readString(javaFile);
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(source);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                log.warn("JavaParser could not parse {}, using raw text", javaFile.getFileName());
                return source;
            }
            CompilationUnit cu = result.getResult().get();
            return format(javaFile, cu);
        } catch (Exception e) {
            log.warn("Semantic analysis failed for {}: {}", javaFile.getFileName(), e.getMessage());
            return source;
        }
    }

    /**
     * Extracts just the call graph: which methods in this file call which other methods.
     * Returns a flat list of "ClassName.callerMethod → callee()" strings.
     */
    public List<String> extractCallGraph(Path javaFile) throws IOException {
        String source = Files.readString(javaFile);
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(source);
            if (!result.isSuccessful() || result.getResult().isEmpty()) return List.of();
            CompilationUnit cu = result.getResult().get();

            List<String> edges = new ArrayList<>();
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls ->
                cls.getMethods().forEach(method -> {
                    String caller = cls.getNameAsString() + "." + method.getNameAsString();
                    method.findAll(MethodCallExpr.class).forEach(call ->
                        edges.add(caller + " → " + call.getNameAsString() + "()"));
                })
            );
            return edges;
        } catch (Exception e) {
            log.warn("Call graph extraction failed for {}: {}", javaFile.getFileName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Extracts the import graph: which external packages/classes this file depends on.
     */
    public List<String> extractImportGraph(Path javaFile) throws IOException {
        String source = Files.readString(javaFile);
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(source);
            if (!result.isSuccessful() || result.getResult().isEmpty()) return List.of();
            return result.getResult().get()
                    .getImports().stream()
                    .map(ImportDeclaration::getNameAsString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Returns {@code true} for files this service can analyse semantically.
     */
    public boolean supports(Path file) {
        return file.toString().endsWith(".java");
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    private String format(Path file, CompilationUnit cu) {
        StringBuilder sb = new StringBuilder();

        // Package
        cu.getPackageDeclaration().ifPresent(pkg ->
                sb.append("package ").append(pkg.getNameAsString()).append(";\n\n"));

        // Import graph (condensed — group by top-level package)
        Set<String> topLevelPackages = cu.getImports().stream()
                .map(i -> {
                    String name = i.getNameAsString();
                    int dot = name.indexOf('.', name.indexOf('.') + 1);
                    return dot > 0 ? name.substring(0, dot) : name;
                })
                .collect(Collectors.toCollection(TreeSet::new));
        if (!topLevelPackages.isEmpty()) {
            sb.append("// Dependencies: ").append(String.join(", ", topLevelPackages)).append("\n\n");
        }

        // Classes / interfaces
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            appendClassSummary(sb, cls);
        });

        return sb.toString();
    }

    private void appendClassSummary(StringBuilder sb, ClassOrInterfaceDeclaration cls) {
        // Javadoc if present
        cls.getJavadocComment().ifPresent(doc ->
                sb.append("/**").append(doc.getContent().trim()).append("*/\n"));

        // Declaration line
        String kind = cls.isInterface() ? "interface" : "class";
        sb.append(cls.getAccessSpecifier().asString()).append(" ").append(kind)
          .append(" ").append(cls.getNameAsString());

        cls.getExtendedTypes().forEach(t -> sb.append(" extends ").append(t.getNameAsString()));
        cls.getImplementedTypes().forEach(t -> sb.append(" implements ").append(t.getNameAsString()));
        sb.append(" {\n\n");

        // Fields
        cls.getFields().forEach(field -> appendField(sb, field));
        if (!cls.getFields().isEmpty()) sb.append("\n");

        // Methods (signatures only — no bodies)
        cls.getMethods().forEach(method -> appendMethodSignature(sb, method));

        sb.append("}\n\n");
    }

    private void appendField(StringBuilder sb, FieldDeclaration field) {
        field.getJavadocComment().ifPresent(doc ->
                sb.append("  /** ").append(doc.getContent().trim()).append(" */\n"));
        sb.append("  ").append(field.getAccessSpecifier().asString()).append(" ")
          .append(field.getElementType().asString()).append(" ");
        field.getVariables().forEach(v -> sb.append(v.getNameAsString()));
        sb.append(";\n");
    }

    private void appendMethodSignature(StringBuilder sb, MethodDeclaration method) {
        method.getJavadocComment().ifPresent(doc ->
                sb.append("  /** ").append(doc.getContent().trim()).append(" */\n"));
        sb.append("  ").append(method.getDeclarationAsString(false, true, true)).append(";\n");

        // Outbound calls — one line summary
        List<String> calls = method.findAll(MethodCallExpr.class).stream()
                .map(MethodCallExpr::getNameAsString)
                .distinct()
                .collect(Collectors.toList());
        if (!calls.isEmpty()) {
            sb.append("  // calls: ").append(String.join(", ", calls)).append("\n");
        }
        sb.append("\n");
    }
}
