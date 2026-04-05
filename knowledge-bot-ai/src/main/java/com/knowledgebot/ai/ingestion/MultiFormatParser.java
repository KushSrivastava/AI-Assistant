package com.knowledgebot.ai.ingestion;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class MultiFormatParser {

    private static final Logger log = LoggerFactory.getLogger(MultiFormatParser.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt",
        "txt", "md", "csv", "json", "xml", "html", "htm",
        "java", "py", "ts", "js", "sql", "yaml", "yml", "properties"
    );

    private final Tika tika = new Tika();

    public boolean isSupported(Path file) {
        String ext = getFileExtension(file);
        return SUPPORTED_EXTENSIONS.contains(ext.toLowerCase());
    }

    public Document parseFile(Path file) throws IOException, TikaException {
        String ext = getFileExtension(file).toLowerCase();
        
        if (isCodeFile(ext)) {
            return parseCodeFile(file, ext);
        }
        
        return parseDocumentFile(file, ext);
    }

    private Document parseCodeFile(Path file, String ext) throws IOException {
        String content = Files.readString(file);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", file.getFileName().toString());
        metadata.put("source", file.toAbsolutePath().toString());
        metadata.put("fileType", "code");
        metadata.put("language", ext);
        return new Document(content, metadata);
    }

    private Document parseDocumentFile(Path file, String ext) throws IOException, TikaException {
        try (InputStream stream = Files.newInputStream(file)) {
            String content = tika.parseToString(stream);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", file.getFileName().toString());
            metadata.put("source", file.toAbsolutePath().toString());
            metadata.put("fileType", "document");
            metadata.put("format", ext);
            return new Document(content, metadata);
        }
    }

    private String getFileExtension(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex + 1) : "";
    }

    private boolean isCodeFile(String ext) {
        return Set.of("java", "py", "ts", "js", "sql", "xml", "yaml", "yml", "properties", "json", "html", "htm")
                .contains(ext);
    }
}
