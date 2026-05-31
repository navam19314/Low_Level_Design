package com.conceptcoding.interviewquestions.hello_all_questions.filesystem;

import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.exception.AlreadyExistsException;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.exception.InvalidPathException;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.exception.NotFoundException;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model.File;
import com.conceptcoding.interviewquestions.hello_all_questions.filesystem.model.FileSystemEntry;

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

        System.out.println("\n--- Move notes.txt up one level ---");
        fs.move("/home/user/notes.txt", "/home/notes.txt");
        System.out.println("notes path: " + fs.get("/home/notes.txt").getPath());

        System.out.println("\n--- Rename /home -> /users ---");
        fs.rename("/home", "users");
        System.out.println("notes path after rename: " + fs.get("/users/notes.txt").getPath());

        System.out.println("\n--- Read file content via get ---");
        File f = (File) fs.get("/users/notes.txt");
        System.out.println("content: " + f.getContent());

        System.out.println("\n--- Duplicate file rejected ---");
        try { fs.createFile("/users/notes.txt", "dup"); }
        catch (AlreadyExistsException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Missing path rejected ---");
        try { fs.get("/nope"); }
        catch (NotFoundException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Cycle on move rejected ---");
        try { fs.move("/users", "/users/user/loop"); }
        catch (InvalidPathException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Delete root rejected ---");
        try { fs.delete("/"); }
        catch (InvalidPathException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Delete /users (whole subtree gone) ---");
        fs.delete("/users");
        try { fs.get("/users"); }
        catch (NotFoundException e) { System.out.println("/users no longer resolves: " + e.getMessage()); }
    }
}
