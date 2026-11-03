package com.babplanner.backend.dev.demo;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시연 데이터 시더 설정.
 *
 * <p>기본은 꺼짐이다. 켜도 local/dev 프로필에서만 동작한다. 생성(seed)과 정리(cleanup)는
 * {@code action}으로 각각 명시해야 하고, 값이 없거나 틀리면 기동에 실패한다.
 */
@ConfigurationProperties(prefix = "bapplanner.demo-data")
public class DemoDataProperties {

    public static final String ACTION_SEED = "seed";
    public static final String ACTION_CLEANUP = "cleanup";

    private boolean enabled = false;
    private String action = "";
    private boolean dryRun = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
