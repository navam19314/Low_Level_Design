package com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model;

/**
 * Composite-pattern component: the shared identity of every node in the tree.
 * Both File (leaf) and Folder (composite) extend this so they share a single
 * "lives in a tree, has a name, has a parent" contract.
 */
public abstract class FileSystemEntry {

    private String name;
    private Folder parent;

    protected FileSystemEntry(String name) {
        this.name = name;
        this.parent = null;
    }

    public String getName()                { return name; }
    public void   setName(String name)     { this.name = name; }
    public Folder getParent()              { return parent; }
    public void   setParent(Folder parent) { this.parent = parent; }

    // Walks up to root, building the path lazily. Root special-cased so we
    // don't emit "//home" — parent-of-root is null, so root.getPath() returns "/".
    public String getPath() {
        if (parent == null) {
            return name;
        }
        String parentPath = parent.getPath();
        return parentPath.equals("/") ? "/" + name : parentPath + "/" + name;
    }

    public abstract boolean isDirectory();
}
