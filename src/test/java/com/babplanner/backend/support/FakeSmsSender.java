package com.babplanner.backend.support;

import com.babplanner.backend.auth.service.SmsSender;
import java.util.ArrayList;
import java.util.List;

/**
 * 실제 발송 없이 마지막으로 요청된 인증번호를 기억한다.
 *
 * <p>테스트가 코드를 알아야 verify 흐름을 검증할 수 있는데, 그 통로를 이
 * Fake로 한정한다. 운영 코드나 API 응답에는 코드가 절대 노출되지 않는다.
 */
public class FakeSmsSender implements SmsSender {

    public record Sent(String phone, String code) {}

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void sendVerificationCode(String phone, String code) {
        sent.add(new Sent(phone, code));
    }

    public String lastCodeFor(String phone) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i).phone().equals(phone)) {
                return sent.get(i).code();
            }
        }
        throw new IllegalStateException("해당 번호로 발송된 인증번호가 없습니다: " + phone);
    }

    public int sentCount() {
        return sent.size();
    }

    public void clear() {
        sent.clear();
    }
}
