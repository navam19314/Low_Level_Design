package com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.exception.FileSystemException;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.model.File;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.model.FileSystemEntry;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.model.Folder;

import java.util.List;

// Orchestrator + facade. Owns the root, parses absolute paths, and exposes the
// core tree-mutating operations as its public API.
//
// One exception type (FileSystemException) with descriptive messages — callers
// catch all FS errors in one clause. If a caller needed to branch on the KIND of
// error, I'd add subtypes then; for the base, message strings are enough.
//
// rename() and move() are deliberately NOT here — they're Step-5 extensions (the
// move-into-descendant cycle check is the interesting part). See INTERVIEW_WALKTHROUGH.md.
// Single-threaded by contract; concurrency upgrade paths are also in Step 5.
public class FileSystem {

    private static final String ROOT      = "/";
    private static final String SEPARATOR = "/";

    private final Folder root;

    public FileSystem() {
        this.root = new Folder(ROOT);
    }

    // ----- Public API -----

    public File createFile(String path, String content) {
        validatePath(path);
        if (ROOT.equals(path)) throw new FileSystemException("Cannot create file at root");
        String fileName = extractName(path);
        Folder parent = resolveParent(path);
        if (parent.hasChild(fileName)) {
            throw new FileSystemException("Entry already exists: " + path);
        }
        File file = new File(fileName, content);
        parent.addChild(file);
        return file;
    }

    public Folder createFolder(String path) {
        validatePath(path);
        if (ROOT.equals(path)) throw new FileSystemException("Root already exists");
        String folderName = extractName(path);
        Folder parent = resolveParent(path);
        if (parent.hasChild(folderName)) {
            throw new FileSystemException("Entry already exists: " + path);
        }
        Folder folder = new Folder(folderName);
        parent.addChild(folder);
        return folder;
    }

    public void delete(String path) {
        validatePath(path);
        if (ROOT.equals(path)) throw new FileSystemException("Cannot delete root");
        Folder parent = resolveParent(path);
        String name = extractName(path);
        FileSystemEntry removed = parent.removeChild(name);
        if (removed == null) {
            throw new FileSystemException("Entry not found: " + path);
        }
    }

    public List<FileSystemEntry> list(String path) {
        FileSystemEntry entry = resolvePath(path);
        if (!entry.isDirectory()) {
            throw new FileSystemException("Cannot list a file: " + path);
        }
        return ((Folder) entry).getChildren();
    }

    public FileSystemEntry get(String path) {
        return resolvePath(path);
    }

    // ----- Path helpers (private) — wrap the messy string-to-tree-node conversion -----

    // Every public method calls this first: absolute, no empty components, no trailing slash.
    private static void validatePath(String path) {
        if (path == null || path.isEmpty()) throw new FileSystemException("Path cannot be null or empty");
        if (!path.startsWith(SEPARATOR))     throw new FileSystemException("Path must be absolute: " + path);
        if (ROOT.equals(path)) return;
        if (path.contains("//"))             throw new FileSystemException("Invalid path (consecutive slashes): " + path);
        if (path.endsWith(SEPARATOR))        throw new FileSystemException("Path must not end with '/': " + path);
    }

    // Walk the tree one component at a time. Throws for: null/empty, non-absolute,
    // missing component, or hitting a file when more components remain.
    private FileSystemEntry resolvePath(String path) {
        validatePath(path);
        if (ROOT.equals(path)) {
            return root;
        }

        String[] parts = path.substring(1).split(SEPARATOR);
        FileSystemEntry current = root;
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new FileSystemException("Invalid path (consecutive slashes): " + path);
            }
            if (!current.isDirectory()) {
                throw new FileSystemException("Not a directory: " + current.getPath());
            }
            FileSystemEntry child = ((Folder) current).getChild(part);
            if (child == null) {
                throw new FileSystemException("Path not found: " + path);
            }
            current = child;
        }
        return current;
    }

    // Returns the parent folder that should contain the final component.
    private Folder resolveParent(String path) {
        if (ROOT.equals(path)) {
            throw new FileSystemException("Root has no parent");
        }
        int lastSlash = path.lastIndexOf(SEPARATOR);
        String parentPath = (lastSlash == 0) ? ROOT : path.substring(0, lastSlash);
        FileSystemEntry parent = resolvePath(parentPath);
        if (!parent.isDirectory()) {
            throw new FileSystemException("Parent is not a directory: " + parentPath);
        }
        return (Folder) parent;
    }

    // "/docs/" would give an empty name: reject it instead of creating a nameless entry
    private String extractName(String path) {
        int lastSlash = path.lastIndexOf(SEPARATOR);
        String name = path.substring(lastSlash + 1);
        if (name.isEmpty()) throw new FileSystemException("Path must end with a name: " + path);
        return name;
    }
}
