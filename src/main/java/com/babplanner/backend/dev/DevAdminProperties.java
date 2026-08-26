package com.babplanner.backend.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로컬 E2E용 관리자 계정 설정.
 *
 * <p>회원가입으로는 관리자를 만들 수 없다. 만들 수 있게 두면 누구나 관리자가
 * 된다. 그래서 로컬에서 관리 화면을 확인할 때 쓸 계정만 이 경로로 만든다.
 *
 * <p>기본값을 두지 않는다. {@code enabled=true}인데 값이 비어 있으면 기동에
 * 실패한다. 운영에서는 프로필 조건 때문에 bean 자체가 만들어지지 않는다.
 */
@ConfigurationProperties(prefix = "bapplanner.dev-admin")
public class DevAdminProperties {

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
