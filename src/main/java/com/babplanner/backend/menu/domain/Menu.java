package com.babplanner.backend.menu.domain;

import com.babplanner.backend.allergy.domain.Allergy;
import com.babplanner.backend.site.domain.DiningSite;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 날짜/시간대별로 실제 제공되는 식단 instance.
 *
 * <p>"2026-08-26 점심 학생식당의 제육덮밥 정식"처럼 하루치 제공분을 뜻한다.
 * 장기 메뉴 template이나 선호메뉴용 dish master가 아니다.
 *
 * <p>식별자는 이 엔티티의 BIGINT id 하나뿐이다. slug나 별도 serverId 개념을
 * 두지 않는다.
 */
@Entity
@Table(name = "menus")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private DiningSite site;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuCategory category;

    @Column(nullable = false, length = 100)
    private String name;

    /** OpenAPI MenuDetail.description. 상세에서만 쓰이며 없을 수 있다. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** OpenAPI MenuSummary.imageUrl. 원격 URL 문자열만 보관하고 업로드는 하지 않는다. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 영양 정보는 전부 nullable이다. 값이 없을 때 0으로 채우지 않는다.
    @Column(name = "calories_kcal")
    private Integer caloriesKcal;

    @Column(name = "protein_g")
    private Double proteinG;

    @Column(name = "carbohydrate_g")
    private Double carbohydrateG;

    @Column(name = "fat_g")
    private Double fatG;

    @Column(name = "sodium_mg")
    private Integer sodiumMg;

    /** 구성 메뉴. 순서를 보존한다. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "menu_items", joinColumns = @JoinColumn(name = "menu_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "name", nullable = false, length = 100)
    private List<String> items = new ArrayList<>();

    /**
     * 알레르기 성분.
     *
     * <p>Stage 13의 allergies master를 그대로 참조한다. 읽기 전용 관계이며
     * cascade를 두지 않는다. 데이터는 migration이 넣는다.
     *
     * <p>join table의 PK가 (menu_id, allergy_id)라 중복이 있을 수 없으므로
     * Set으로 둔다. List로 두면 다른 컬렉션과 함께 fetch될 때 곱집합 때문에
     * 같은 성분이 여러 번 나온다.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "menu_allergies",
            joinColumns = @JoinColumn(name = "menu_id"),
            inverseJoinColumns = @JoinColumn(name = "allergy_id"))
    private Set<Allergy> allergies = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Menu() {
        // JPA 전용
    }

    public Menu(
            DiningSite site,
            LocalDate serviceDate,
            MealType mealType,
            MenuCategory category,
            String name,
            List<String> items,
            Instant now) {
        this.site = site;
        this.serviceDate = serviceDate;
        this.mealType = mealType;
        this.category = category;
        this.name = name;
        this.items = new ArrayList<>(items);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public DiningSite getSite() {
        return site;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public MealType getMealType() {
        return mealType;
    }

    public MenuCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Integer getCaloriesKcal() {
        return caloriesKcal;
    }

    public Double getProteinG() {
        return proteinG;
    }

    public Double getCarbohydrateG() {
        return carbohydrateG;
    }

    public Double getFatG() {
        return fatG;
    }

    public Integer getSodiumMg() {
        return sodiumMg;
    }

    public List<String> getItems() {
        return items;
    }

    public Set<Allergy> getAllergies() {
        return allergies;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
