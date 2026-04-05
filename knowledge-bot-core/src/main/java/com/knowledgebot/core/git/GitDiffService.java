package com.knowledgebot.core.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;

@Service
public class GitDiffService {
    private static final Logger log = LoggerFactory.getLogger(GitDiffService.class);

    public String getLocalDiff(Path repositoryPath) {
        try (Git git = Git.open(repositoryPath.toFile())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(git.getRepository());
                
                // Diff between HEAD and working tree
                for (DiffEntry entry : git.diff().call()) {
                    formatter.format(entry);
                }
            }
            String diff = out.toString();
            if (diff.isEmpty()) {
                return "No structured diff found between HEAD and working directory.";
            }
            return diff;
        } catch (Exception e) {
            log.error("JGit computation failed", e);
            return "Failed to parse Git diff: " + e.getMessage();
        }
    }
}
