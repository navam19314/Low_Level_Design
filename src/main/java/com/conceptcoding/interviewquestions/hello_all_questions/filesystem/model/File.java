package com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model;

/** Leaf node — stores content, has no children. */
public class File extends FileSystemEntry {

    private String content;

    public File(String name, String content) {
        super(name);
        this.content = content;
    }

    public String getContent()                 { return content; }
    public void   setContent(String content)   { this.content = content; }

    @Override
    public boolean isDirectory() { return false; }
}
