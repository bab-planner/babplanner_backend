-- name은 API의 restaurantName으로 그대로 나간다.
-- code는 내부 식별용이며 API 응답에 노출하지 않는다.
INSERT INTO dining_sites (name, code, active, sort_order) VALUES
    ('호서대학교 학식', 'STUDENT', TRUE, 1),
    ('교직원 식당', 'STAFF', TRUE, 2);
