package com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.exception;

// Common base — lets callers catch all file-system errors with one catch clause.
public class FileSystemException extends RuntimeException {
    public FileSystemException(String message) { super(message); }
}
