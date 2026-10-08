package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.model;

//            pause            resume
//  QUEUED ─────────→ PAUSED ─────────→ QUEUED (or straight back to DOWNLOADING)
//    │  worker picks it up   ↑ pause
//    ↓                       │
//  DOWNLOADING ──────────────┘ ──→ COMPLETED / FAILED
//  any non-final state ──cancel──→ CANCELLED
public enum DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED;

    public boolean isFinal() { return this == COMPLETED || this == FAILED || this == CANCELLED; }
}
