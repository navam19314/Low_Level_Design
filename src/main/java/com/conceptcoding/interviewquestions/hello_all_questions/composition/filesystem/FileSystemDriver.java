package com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.exception.FileSystemException;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.model.File;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.model.FileSystemEntry;

public class FileSystemDriver {

    public static void main(String[] args) {
        FileSystem fs = new FileSystem();

        System.out.println("--- Build a small tree ---");
        fs.createFolder("/home");
        fs.createFolder("/home/user");
        fs.createFile("/home/user/notes.txt", "hello world");
        fs.createFile("/readme.txt", "top-level file");
        System.out.println("notes path: " + fs.get("/home/user/notes.txt").getPath());

        System.out.println("\n--- list /home ---");
        for (FileSystemEntry e : fs.list("/home")) {
            System.out.println("  " + e.getName() + (e.isDirectory() ? "/" : ""));
        }

        System.out.println("\n--- Read file content via get ---");
        File f = (File) fs.get("/home/user/notes.txt");
        System.out.println("content: " + f.getContent());

        System.out.println("\n--- Composite: sizes roll up the tree ---");
        fs.createFile("/home/user/todo.txt", "buy milk");
        System.out.println("  /home/user = " + fs.get("/home/user").getSize() + " bytes (expect 11 + 8 = 19)");
        System.out.println("  /         = " + fs.get("/").getSize() + " bytes (expect 19 + 14 = 33)");

        System.out.println("\n--- Bad paths rejected ---");
        try { fs.createFile("/home/", "x"); }
        catch (FileSystemException e) { System.out.println("Rejected: " + e.getMessage()); }
        try { fs.createFile("/home//x.txt", "x"); }
        catch (FileSystemException e) { System.out.println("Rejected: " + e.getMessage()); }
        try { fs.createFile("relative.txt", "x"); }
        catch (FileSystemException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Duplicate file rejected ---");
        try { fs.createFile("/home/user/notes.txt", "dup"); }
        catch (FileSystemException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Missing path rejected ---");
        try { fs.get("/nope"); }
        catch (FileSystemException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Listing a file rejected ---");
        try { fs.list("/readme.txt"); }
        catch (FileSystemException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Delete root rejected ---");
        try { fs.delete("/"); }
        catch (FileSystemException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Delete /home (whole subtree gone) ---");
        fs.delete("/home");
        try { fs.get("/home"); }
        catch (FileSystemException e) { System.out.println("/home no longer resolves: " + e.getMessage()); }
    }
}
