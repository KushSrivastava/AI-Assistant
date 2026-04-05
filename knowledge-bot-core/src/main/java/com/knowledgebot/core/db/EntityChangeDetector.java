package com.knowledgebot.core.db;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EntityChangeDetector {
    
    public String extractEntitySchema(Path entityFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(Files.readString(entityFile));
            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
            
            return classes.stream()
                .filter(c -> c.isAnnotationPresent("Entity"))
                .map(c -> "Entity: " + c.getNameAsString() + ", Fields: " + c.getFields().toString())
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Failed to parse entity: " + e.getMessage();
        }
    }
}
