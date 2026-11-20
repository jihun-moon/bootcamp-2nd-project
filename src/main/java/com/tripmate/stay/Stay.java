package com.tripmate.stay;

import com.tripmate.trip.Member;
import com.tripmate.trip.Trip;
import jakarta.persistence.*;

import java.util.EnumSet;
import java.util.Set;

@Entity
public class Stay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member addedBy;

    private String title;

    // 예약 사이트 링크는 추적 값이 붙어 255자를 쉽게 넘는다. 요청 검사(StayDto, 2000자)와 길이를 맞춘다
    @Column(length = 2000)
    private String url;

    @Column(length = 2000)
    private String imageUrl;

    // 크롤링 대신 올린 사람이 체크한다. 그래서 정해진 목록(enum) 안에서만 고른다
    @ElementCollection
    @CollectionTable(name = "stay_amenity")
    @Enumerated(EnumType.STRING)
    @Column(name = "amenity")
    private Set<Amenity> amenities = EnumSet.noneOf(Amenity.class);

    protected Stay() {
    }

    public Stay(Trip trip, Member addedBy, String title, String url, String imageUrl, Set<Amenity> amenities) {
        this.trip = trip;
        this.addedBy = addedBy;
        this.title = title;
        this.url = url;
        this.imageUrl = imageUrl;
        this.amenities.addAll(amenities);
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getImageUrl() { return imageUrl; }
    public Set<Amenity> getAmenities() { return amenities; }
}
