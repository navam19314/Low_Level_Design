package com.conceptcoding.interviewquestions.hello_all_questions.filesystem;

import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.exception.AlreadyExistsException;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.exception.InvalidPathException;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.exception.NotADirectoryException;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.exception.NotFoundException;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model.File;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model.FileSystemEntry;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model.Folder;

import java.util.List;

/**
 * Orchestrator + facade. Owns the root, parses absolute paths, and exposes all
 * tree-mutating operations as its public API. Single-threaded by contract — see
 * Step 5 of INTERVIEW_WALKTHROUGH.md for the synchronized / per-folder-lock /
 * read-write-lock upgrade paths.
 */
public class FileSystem {

    private static final String ROOT      = "/";
    private static final String SEPARATOR = "/";

    private final Folder root;

    public FileSystem() {
        this.root = new Folder(ROOT);
    }

    // ----- Public API -----

    public File createFile(String path, String content) {
        if (ROOT.equals(path)) throw new InvalidPathException("Cannot create file at root");
        Folder parent = resolveParent(path);
        String fileName = extractName(path);
        if (parent.hasChild(fileName)) {
            throw new AlreadyExistsException("Entry already exists: " + path);
        }
        File file = new File(fileName, content);
        parent.addChild(file);
        return file;
    }

    public Folder createFolder(String path) {
        if (ROOT.equals(path)) throw new AlreadyExistsException("Root already exists");
        Folder parent = resolveParent(path);
        String folderName = extractName(path);
        if (parent.hasChild(folderName)) {
            throw new AlreadyExistsException("Entry already exists: " + path);
        }
        Folder folder = new Folder(folderName);
        parent.addChild(folder);
        return folder;
    }

    public void delete(String path) {
        if (ROOT.equals(path)) throw new InvalidPathException("Cannot delete root");
        Folder parent = resolveParent(path);
        String name = extractName(path);
        FileSystemEntry removed = parent.removeChild(name);
        if (removed == null) {
            throw new NotFoundException("Entry not found: " + path);
        }
    }

    public List<FileSystemEntry> list(String path) {
        FileSystemEntry entry = resolvePath(path);
        if (!entry.isDirectory()) {
            throw new NotADirectoryException("Cannot list a file: " + path);
        }
        return ((Folder) entry).getChildren();
    }

    public FileSystemEntry get(String path) {
        return resolvePath(path);
    }

    public void rename(String path, String newName) {
        if (ROOT.equals(path)) throw new InvalidPathException("Cannot rename root");
        if (newName == null || newName.isEmpty() || newName.contains(SEPARATOR)) {
            throw new InvalidPathException("Invalid name: " + newName);
        }
        Folder parent = resolveParent(path);
        String oldName = extractName(path);
        if (!parent.hasChild(oldName)) {
            throw new NotFoundException("Entry not found: " + path);
        }
        if (parent.hasChild(newName)) {
            throw new AlreadyExistsException("Sibling already exists: " + newName);
        }
        // Remove-rename-readd: the map is keyed by name, so we must re-insert.
        FileSystemEntry entry = parent.removeChild(oldName);
        entry.setName(newName);
        parent.addChild(entry);
    }

    public void move(String srcPath, String destPath) {
        if (ROOT.equals(srcPath)) throw new InvalidPathException("Cannot move root");

        Folder srcParent = resolveParent(srcPath);
        String srcName = extractName(srcPath);
        FileSystemEntry entry = srcParent.getChild(srcName);
        if (entry == null) {
            throw new NotFoundException("Source not found: " + srcPath);
        }

        Folder destParent = resolveParent(destPath);
        String destName = extractName(destPath);

        // Cycle check: only relevant for directories. Walk up from destParent;
        // if we ever hit `entry`, the move would create an impossible loop.
        if (entry.isDirectory()) {
            Folder cursor = destParent;
            while (cursor != null) {
                if (cursor == entry) {
                    throw new InvalidPathException(
                            "Cannot move folder into itself or a descendant: " + srcPath + " -> " + destPath);
                }
                cursor = cursor.getParent();
            }
        }

        if (destParent.hasChild(destName)) {
            throw new AlreadyExistsException("Destination already exists: " + destPath);
        }

        srcParent.removeChild(srcName);
        entry.setName(destName);
        destParent.addChild(entry);
    }

    // ----- Path helpers (private) — wrap the messy string-to-tree-node conversion -----

    // Walk the tree one component at a time. Throws for: null/empty, non-absolute,
    // missing component, or hitting a file when more components remain.
    private FileSystemEntry resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new InvalidPathException("Path cannot be null or empty");
        }
        if (!path.startsWith(SEPARATOR)) {
            throw new InvalidPathException("Path must be absolute: " + path);
        }
        if (ROOT.equals(path)) {
            return root;
        }

        String[] parts = path.substring(1).split(SEPARATOR);
        FileSystemEntry current = root;
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new InvalidPathException("Invalid path (consecutive slashes): " + path);
            }
            if (!current.isDirectory()) {
                throw new NotADirectoryException("Not a directory: " + current.getPath());
            }
            FileSystemEntry child = ((Folder) current).getChild(part);
            if (child == null) {
                throw new NotFoundException("Path not found: " + path);
            }
            current = child;
        }
        return current;
    }

    // Returns the parent folder that should contain the final component.
    private Folder resolveParent(String path) {
        if (ROOT.equals(path)) {
            throw new InvalidPathException("Root has no parent");
        }
        int lastSlash = path.lastIndexOf(SEPARATOR);
        String parentPath = (lastSlash == 0) ? ROOT : path.substring(0, lastSlash);
        FileSystemEntry parent = resolvePath(parentPath);
        if (!parent.isDirectory()) {
            throw new NotADirectoryException("Parent is not a directory: " + parentPath);
        }
        return (Folder) parent;
    }

    private String extractName(String path) {
        int lastSlash = path.lastIndexOf(SEPARATOR);
        return path.substring(lastSlash + 1);
    }
}
