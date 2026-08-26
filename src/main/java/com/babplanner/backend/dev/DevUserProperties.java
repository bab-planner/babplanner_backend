package com.babplanner.backend.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로컬 E2E용 개발 사용자 설정.
 *
 * <p>실제 SMS 어댑터가 없어 회원가입 화면으로는 계정을 만들 수 없다. 그래서
 * 로그인 E2E에 쓸 계정만 로컬/개발 환경에서 직접 만든다.
 *
 * <p>기본값을 두지 않는다. {@code enabled=true}인데 값이 비어 있으면 기동에
 * 실패한다. 인증 절차를 우회하는 endpoint를 만들거나 인증번호를 노출하는
 * 대신, 계정 생성 자체를 운영 경로 밖으로 뺀 것이다.
 */
@ConfigurationProperties(prefix = "bapplanner.dev-user")
public class DevUserProperties {

    private boolean enabled;
    private String phone;
    private String password;
    private String studentId;
    private String name;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
