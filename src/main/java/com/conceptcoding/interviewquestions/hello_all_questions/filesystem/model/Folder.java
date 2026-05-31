package com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Composite node — contains other entries (files or folders), keyed by name for O(1) lookup. */
public class Folder extends FileSystemEntry {

    // LinkedHashMap preserves insertion order so list() output is deterministic.
    private final Map<String, FileSystemEntry> children = new LinkedHashMap<>();

    public Folder(String name) {
        super(name);
    }

    @Override
    public boolean isDirectory() { return true; }

    // Bidirectional consistency: setting parent here keeps the back-reference in sync.
    // Returns false on null or name collision; callers decide whether that's a fatal error.
    public boolean addChild(FileSystemEntry entry) {
        if (entry == null || children.containsKey(entry.getName())) {
            return false;
        }
        children.put(entry.getName(), entry);
        entry.setParent(this);
        return true;
    }

    // Clears the entry's back-reference too, so detached entries don't carry stale pointers.
    public FileSystemEntry removeChild(String name) {
        FileSystemEntry entry = children.remove(name);
        if (entry != null) {
            entry.setParent(null);
        }
        return entry;
    }

    public FileSystemEntry getChild(String name) { return children.get(name); }
    public boolean         hasChild(String name) { return children.containsKey(name); }

    // Defensive copy — callers can't mutate our internal map.
    public List<FileSystemEntry> getChildren()   { return new ArrayList<>(children.values()); }
}
