package com.babplanner.backend.dev.demo;

import java.util.LinkedHashMap;
import java.util.Map;

/** 시더/정리 한 번의 결과. 종류별로 새로 처리한 건수와 이미 있어 건너뛴 건수를 센다. */
public class DemoReport {

    private final String action;
    private final boolean dryRun;
    private final Map<String, Integer> done = new LinkedHashMap<>();
    private final Map<String, Integer> skipped = new LinkedHashMap<>();

    public DemoReport(String action, boolean dryRun) {
        this.action = action;
        this.dryRun = dryRun;
    }

    void done(String type) {
        done.merge(type, 1, Integer::sum);
    }

    void done(String type, int count) {
        if (count > 0) {
            done.merge(type, count, Integer::sum);
        }
    }

    void skipped(String type) {
        skipped.merge(type, 1, Integer::sum);
    }

    public int doneCount(String type) {
        return done.getOrDefault(type, 0);
    }

    public int skippedCount(String type) {
        return skipped.getOrDefault(type, 0);
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public String summary() {
        return action + (dryRun ? " (dry-run, 롤백됨)" : "") + " 처리=" + done + " 건너뜀=" + skipped;
    }

    @Override
    public String toString() {
        return summary();
    }
}
