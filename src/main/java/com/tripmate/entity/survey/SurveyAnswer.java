package com.tripmate.entity.survey;

import com.tripmate.entity.role.Chore;
import com.tripmate.entity.stay.Amenity;
import com.tripmate.entity.trip.Member;
import jakarta.persistence.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// 한 사람당 한 장. 다시 하면 새로 만들지 않고 고친다
@Entity
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(unique = true)
    private Member member;

    @Enumerated(EnumType.STRING)
    private ActivityStyle activity;

    @Enumerated(EnumType.STRING)
    private PlanningStyle planning;

    private boolean hasCar;
    private boolean separateDeparture;

    // 차로 갈 때 출발하는 동네(예: 서울 마포구). 이동·회비 근거의 길찾기에 쓴다. 본인과 방장만 본다
    @Column(length = 30)
    private String departure;

    // 본인과 방장만 본다. 다른 사람에게 내보내는 응답에는 이 칸을 넣지 않는다
    private boolean mobilityLimited;

    @ElementCollection
    @CollectionTable(name = "survey_avoid")
    @Enumerated(EnumType.STRING)
    @Column(name = "chore")
    private Set<Chore> avoid = EnumSet.noneOf(Chore.class);

    // 숙소 조건마다 1~5점. 그룹 차트의 평균·최솟값과 숙소 점수가 여기서 나온다
    @ElementCollection
    @CollectionTable(name = "survey_preference")
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "amenity")
    @Column(name = "score")
    private Map<Amenity, Integer> preferences = new EnumMap<>(Amenity.class);

    protected SurveyAnswer() {
    }

    public SurveyAnswer(Member member) {
        this.member = member;
    }

    public void update(ActivityStyle activity, PlanningStyle planning, boolean hasCar, boolean separateDeparture,
                       String departure, boolean mobilityLimited, Set<Chore> avoid, Map<Amenity, Integer> preferences) {
        this.activity = activity;
        this.planning = planning;
        this.hasCar = hasCar;
        this.separateDeparture = separateDeparture;
        this.departure = departure;
        this.mobilityLimited = mobilityLimited;
        this.avoid.clear();
        this.avoid.addAll(avoid);
        this.preferences.clear();
        this.preferences.putAll(preferences);
        member.finishSurvey();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public ActivityStyle getActivity() { return activity; }
    public PlanningStyle getPlanning() { return planning; }
    public boolean isHasCar() { return hasCar; }
    public boolean isSeparateDeparture() { return separateDeparture; }
    public String getDeparture() { return departure; }
    public boolean isMobilityLimited() { return mobilityLimited; }
    public Set<Chore> getAvoid() { return avoid; }
    public Map<Amenity, Integer> getPreferences() { return preferences; }
}
