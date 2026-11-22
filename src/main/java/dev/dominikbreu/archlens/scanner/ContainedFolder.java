package dev.dominikbreu.archlens.scanner;

import dev.dominikbreu.archlens.io.ReadBoundary;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.compiler.SpoonFile;
import spoon.compiler.SpoonFolder;
import spoon.compiler.SpoonResourceHelper;
import spoon.support.compiler.FileSystemFolder;

/**
 * A source folder that Spoon walks in its usual order, minus links out of the indexed projects and
 * links back to a folder that is already being walked.
 */
final class ContainedFolder extends FileSystemFolder {

    /** Canonical paths of this folder and those walked to reach it. */
    private final Set<File> walked;

    ContainedFolder(File directory) {
        this(directory, Set.of());
    }

    private ContainedFolder(File directory, Set<File> walkedBefore) {
        super(directory);
        Set<File> all = new HashSet<>(walkedBefore);
        all.add(toFile());
        this.walked = Set.copyOf(all);
    }

    /**
     * Lists this folder's files, without Java files that resolve outside the indexed projects.
     *
     * @return files to analyze
     */
    @Override
    public List<SpoonFile> getFiles() {
        return super.getFiles().stream()
                .filter(file -> !file.isJava() || ReadBoundary.allows(file.toFile()))
                .toList();
    }

    /**
     * Lists the subfolders that are inside the indexed projects and not already being walked.
     *
     * @return folders to descend into
     */
    @Override
    public List<SpoonFolder> getSubFolders() {
        List<SpoonFolder> folders = new ArrayList<>();
        File[] children = toFile().listFiles();
        if (children == null) return folders;
        for (File child : children) {
            if (SpoonResourceHelper.isArchive(child) || !child.isDirectory() || !ReadBoundary.allows(child)) continue;
            ContainedFolder folder = new ContainedFolder(child, walked);
            if (!walked.contains(folder.toFile())) folders.add(folder);
        }
        return folders;
    }

    /**
     * Compares folders by canonical path only; how a folder was reached does not matter.
     *
     * @param other object to compare with
     * @return whether both are the same folder
     */
    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    /**
     * Hashes the canonical path, consistent with {@link #equals}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
