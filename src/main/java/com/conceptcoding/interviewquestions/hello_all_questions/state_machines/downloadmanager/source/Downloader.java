package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.source;

// Where bytes come from (HTTP in real life). Behind an interface so the manager can be
// tested with a fake, and so FTP / S3 sources plug in later.
// Reads from 'offset', which is what makes RESUME possible: we never restart from byte 0.
public interface Downloader {
    // returns how many bytes were read (0 < n <= maxBytes); throws TransientDownloadException
    // for retryable errors, anything else for permanent ones (404, disk full)
    int readChunk(String url, long offset, int maxBytes);
}
