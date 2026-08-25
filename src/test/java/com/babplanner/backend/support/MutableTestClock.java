package com.babplanner.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트에서 시간을 앞으로 밀 수 있는 Clock.
 *
 * <p>TTL 검증에 Thread.sleep을 쓰지 않기 위해 존재한다.
 */
public class MutableTestClock extends Clock {

    private static final Instant START = Instant.parse("2026-08-26T00:00:00Z");

    private Instant now = START;

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void advance(Duration amount) {
        now = now.plus(amount);
    }

    public void reset() {
        now = START;
    }
}
