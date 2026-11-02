package com.frimer.launcher.core;

/** Колбэк прогресса: сколько уже сделано из общего объёма (в байтах или в штуках). */
@FunctionalInterface
public interface ProgressListener {

    void update(long done, long total);

    ProgressListener NONE = (done, total) -> {
    };
}
