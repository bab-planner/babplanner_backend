-- 개발/테스트용 고정 샘플 식단. 실제 학교 운영 식단이 아니다.
--
-- 날짜를 고정값으로 둔다. production migration에 CURRENT_DATE 같은 동적
-- 값을 넣으면 환경마다 다른 데이터가 생기고 재현이 불가능해진다.
-- "오늘 날짜" 데이터가 필요한 로컬 개발은 DevMenuSeeder(local/dev 프로필)가 담당한다.

-- 2026-08-26 점심 · 학생식당
INSERT INTO menus (site_id, service_date, meal_type, category, name, description,
                   image_url, calories_kcal, protein_g, carbohydrate_g, fat_g, sodium_mg,
                   created_at, updated_at)
SELECT s.id, DATE '2026-08-26', 'LUNCH', 'KOREAN', '제육덮밥 정식',
       '국산 돼지고기를 사용한 매콤달콤한 제육볶음 정식',
       NULL, 716, 30.9, 74.1, 30.3, 1100,
       TIMESTAMP '2026-08-25 00:00:00', TIMESTAMP '2026-08-25 00:00:00'
FROM dining_sites s WHERE s.code = 'STUDENT';

INSERT INTO menus (site_id, service_date, meal_type, category, name, description,
                   image_url, calories_kcal, protein_g, carbohydrate_g, fat_g, sodium_mg,
                   created_at, updated_at)
SELECT s.id, DATE '2026-08-26', 'LUNCH', 'KOREAN', '얼큰 온쫄면 정식',
       NULL, NULL, 785, NULL, NULL, NULL, NULL,
       TIMESTAMP '2026-08-25 00:00:00', TIMESTAMP '2026-08-25 00:00:00'
FROM dining_sites s WHERE s.code = 'STUDENT';

-- 영양정보가 전혀 없는 경우. null이 null로 유지되는지 확인하는 fixture다.
INSERT INTO menus (site_id, service_date, meal_type, category, name, description,
                   image_url, calories_kcal, protein_g, carbohydrate_g, fat_g, sodium_mg,
                   created_at, updated_at)
SELECT s.id, DATE '2026-08-26', 'LUNCH', 'SNACK', '떡볶이 세트',
       NULL, NULL, NULL, NULL, NULL, NULL, NULL,
       TIMESTAMP '2026-08-25 00:00:00', TIMESTAMP '2026-08-25 00:00:00'
FROM dining_sites s WHERE s.code = 'STUDENT';

-- 2026-08-26 저녁 · 교직원식당
INSERT INTO menus (site_id, service_date, meal_type, category, name, description,
                   image_url, calories_kcal, protein_g, carbohydrate_g, fat_g, sodium_mg,
                   created_at, updated_at)
SELECT s.id, DATE '2026-08-26', 'DINNER', 'WESTERN', '치킨 스테이크 정식',
       NULL, NULL, 820, 41.2, 60.5, 38.1, 980,
       TIMESTAMP '2026-08-25 00:00:00', TIMESTAMP '2026-08-25 00:00:00'
FROM dining_sites s WHERE s.code = 'STAFF';

-- 2026-08-27 아침 · 학생식당 (날짜 필터 검증용)
INSERT INTO menus (site_id, service_date, meal_type, category, name, description,
                   image_url, calories_kcal, protein_g, carbohydrate_g, fat_g, sodium_mg,
                   created_at, updated_at)
SELECT s.id, DATE '2026-08-27', 'BREAKFAST', 'KOREAN', '전복 비빔밥 정식',
       NULL, NULL, 785, NULL, NULL, NULL, NULL,
       TIMESTAMP '2026-08-25 00:00:00', TIMESTAMP '2026-08-25 00:00:00'
FROM dining_sites s WHERE s.code = 'STUDENT';

-- 구성 메뉴
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 0, '제육덮밥' FROM menus m WHERE m.name = '제육덮밥 정식';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 1, '김치' FROM menus m WHERE m.name = '제육덮밥 정식';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 2, '콩나물국' FROM menus m WHERE m.name = '제육덮밥 정식';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 3, '요구르트' FROM menus m WHERE m.name = '제육덮밥 정식';

INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 0, '올쫄면' FROM menus m WHERE m.name = '얼큰 온쫄면 정식';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 1, '단무지무침' FROM menus m WHERE m.name = '얼큰 온쫄면 정식';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 2, '동그랑땡' FROM menus m WHERE m.name = '얼큰 온쫄면 정식';

INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 0, '떡볶이' FROM menus m WHERE m.name = '떡볶이 세트';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 1, '튀김' FROM menus m WHERE m.name = '떡볶이 세트';

INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 0, '치킨 스테이크' FROM menus m WHERE m.name = '치킨 스테이크 정식';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 1, '감자 퓨레' FROM menus m WHERE m.name = '치킨 스테이크 정식';

INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 0, '전복 비빔밥' FROM menus m WHERE m.name = '전복 비빔밥 정식';
INSERT INTO menu_items (menu_id, sort_order, name)
SELECT m.id, 1, '미역국' FROM menus m WHERE m.name = '전복 비빔밥 정식';

-- 알레르기 성분. allergies master의 id를 이름으로 찾아 연결한다.
INSERT INTO menu_allergies (menu_id, allergy_id)
SELECT m.id, a.id FROM menus m, allergies a
WHERE m.name = '제육덮밥 정식' AND a.name IN ('돼지고기', '대두', '밀');

INSERT INTO menu_allergies (menu_id, allergy_id)
SELECT m.id, a.id FROM menus m, allergies a
WHERE m.name = '얼큰 온쫄면 정식' AND a.name IN ('밀', '난류(계란)');

INSERT INTO menu_allergies (menu_id, allergy_id)
SELECT m.id, a.id FROM menus m, allergies a
WHERE m.name = '치킨 스테이크 정식' AND a.name IN ('닭고기', '우유');
