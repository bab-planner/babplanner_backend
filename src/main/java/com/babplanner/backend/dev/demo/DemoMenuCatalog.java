package com.babplanner.backend.dev.demo;

import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.menu.domain.MenuCategory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 시연용 식단 목록. 실제 학교 운영 식단이 아니라 화면 시연을 위한 가상 메뉴다.
 *
 * <p>날짜의 epoch day로 7일 주기로 돌려 고르므로 같은 날짜는 항상 같은 식단이 된다.
 */
final class DemoMenuCatalog {

    record Spec(
            MealType mealType,
            MenuCategory category,
            String name,
            List<String> items,
            int caloriesKcal,
            double proteinG,
            double carbohydrateG,
            double fatG,
            int sodiumMg) {}

    // 분류|이름|구성(쉼표)|kcal|단백질|탄수화물|지방|나트륨
    private static final String[] BREAKFAST = {
        "KOREAN|전복죽 정식|전복죽,계란말이,김치,과일|520|18.5|78.0|12.4|890",
        "KOREAN|북어국 백반|북어국,잡곡밥,멸치볶음,김치|560|24.1|84.2|9.8|1020",
        "WESTERN|토스트 세트|햄치즈토스트,수프,샐러드,우유|480|20.3|52.6|20.1|870",
        "KOREAN|김치볶음밥|김치볶음밥,계란후라이,미역국,단무지|610|19.7|88.3|18.2|1150",
        "WESTERN|샌드위치 세트|에그샌드위치,과일,우유|450|17.8|55.4|16.9|720",
        "OTHER|오트밀 요거트|오트밀,요거트,견과류,바나나|400|14.2|62.1|10.3|180",
        "KOREAN|계란죽 정식|계란죽,장조림,김치,숭늉|500|17.1|80.2|9.5|940"
    };
    private static final String[] LUNCH_A = {
        "KOREAN|김치찌개 백반|김치찌개,잡곡밥,계란말이,시금치나물|690|28.4|92.0|20.1|1580",
        "KOREAN|불고기 정식|불고기,잡곡밥,된장국,깍두기|720|32.5|88.7|24.3|1320",
        "KOREAN|닭갈비 덮밥|닭갈비,밥,미소된장국,단무지|750|34.0|96.2|22.8|1410",
        "KOREAN|순두부찌개 정식|순두부찌개,밥,고등어구이,김치|680|30.2|85.5|22.0|1490",
        "KOREAN|제육볶음 정식|제육볶음,밥,콩나물국,김치|730|31.8|90.4|25.6|1380",
        "KOREAN|비빔밥 정식|비빔밥,된장국,나물,과일|650|22.4|102.0|14.7|1210",
        "KOREAN|갈비탕 정식|갈비탕,밥,잡채,깍두기|710|36.5|80.1|23.4|1650"
    };
    private static final String[] LUNCH_B = {
        "CHINESE|짜장면 세트|짜장면,군만두,단무지,볶음김치|820|24.1|130.5|22.0|1850",
        "CHINESE|짬뽕밥 세트|짬뽕밥,탕수육,단무지|840|30.2|118.0|27.5|1990",
        "JAPANESE|치킨가라아게 덮밥|가라아게,밥,미소국,샐러드|780|33.4|98.6|26.1|1320",
        "JAPANESE|우동 정식|우동,유부초밥,샐러드|650|20.5|106.0|13.8|1780",
        "CHINESE|마파두부 덮밥|마파두부,밥,계란국,김치|710|26.3|96.7|22.9|1560",
        "JAPANESE|연어 덮밥|연어,밥,된장국,샐러드|700|31.0|92.4|20.2|1210",
        "CHINESE|볶음짬뽕 세트|볶음짬뽕,군만두,단무지|860|27.8|124.1|28.9|2010"
    };
    private static final String[] LUNCH_C = {
        "SNACK|떡볶이 세트|떡볶이,튀김,순대,어묵국|760|18.5|120.4|22.1|1750",
        "WESTERN|돈까스 정식|돈까스,밥,수프,샐러드|880|34.7|96.5|36.2|1480",
        "WESTERN|치즈 오므라이스|오므라이스,수프,샐러드,피클|790|24.0|104.3|27.8|1390",
        "SNACK|라볶이 세트|라볶이,김밥,어묵국|770|20.1|124.5|20.7|1820",
        "WESTERN|함박스테이크 정식|함박스테이크,밥,수프,샐러드|860|33.5|92.0|38.4|1420",
        "WESTERN|크림파스타 세트|크림파스타,마늘빵,샐러드|820|24.8|98.2|35.1|1260",
        "SNACK|김밥 세트|참치김밥,우동국물,단무지|700|21.0|110.8|18.6|1580"
    };
    private static final String[] DINNER = {
        "KOREAN|부대찌개 정식|부대찌개,밥,계란말이,김치|790|31.6|88.0|33.5|1980",
        "JAPANESE|카레라이스 정식|카레라이스,샐러드,피클|760|22.6|122.0|20.4|1340",
        "KOREAN|삼겹살 구이 정식|삼겹살,밥,된장찌개,쌈채소|900|36.0|84.1|45.3|1520",
        "KOREAN|닭볶음탕 정식|닭볶음탕,밥,콩나물무침,김치|780|38.1|86.5|26.2|1710",
        "WESTERN|스테이크 정식|스테이크,구운야채,빵,수프|870|42.3|70.4|40.8|1280",
        "KOREAN|오징어볶음 덮밥|오징어볶음,밥,미역국,김치|740|29.4|98.6|21.7|1630",
        "KOREAN|생선구이 정식|고등어구이,밥,된장국,나물|680|33.1|82.4|21.5|1390"
    };

    private DemoMenuCatalog() {}

    /** 한 식당의 하루 식단. siteIndex 0은 학생식당 구성, 그 외는 교직원식당 구성이다. */
    static List<Spec> forDay(int siteIndex, LocalDate date) {
        int d = (int) Math.floorMod(date.toEpochDay(), 7L);
        List<Spec> result = new ArrayList<>();
        if (siteIndex == 0) {
            result.add(parse(MealType.BREAKFAST, BREAKFAST[d]));
            result.add(parse(MealType.LUNCH, LUNCH_A[d]));
            result.add(parse(MealType.LUNCH, LUNCH_B[(d + 2) % 7]));
            result.add(parse(MealType.LUNCH, LUNCH_C[(d + 4) % 7]));
            result.add(parse(MealType.DINNER, DINNER[d]));
        } else {
            result.add(parse(MealType.LUNCH, LUNCH_A[(d + 3) % 7]));
            result.add(parse(MealType.DINNER, DINNER[(d + 2) % 7]));
        }
        return result;
    }

    private static Spec parse(MealType mealType, String row) {
        String[] f = row.split("[|]");
        return new Spec(
                mealType,
                MenuCategory.valueOf(f[0]),
                f[1],
                Arrays.asList(f[2].split(",")),
                Integer.parseInt(f[3]),
                Double.parseDouble(f[4]),
                Double.parseDouble(f[5]),
                Double.parseDouble(f[6]),
                Integer.parseInt(f[7]));
    }
}
