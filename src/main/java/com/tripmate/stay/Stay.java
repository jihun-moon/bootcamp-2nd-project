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

    // 1박 가격(원). 링크의 구조화 데이터에서 읽어 채우고 올린 사람이 고칠 수 있다. 모르면 null
    private Integer price;

    // 크롤링 대신 올린 사람이 체크한다. 그래서 정해진 목록(enum) 안에서만 고른다
    @ElementCollection
    @CollectionTable(name = "stay_amenity")
    @Enumerated(EnumType.STRING)
    @Column(name = "amenity")
    private Set<Amenity> amenities = EnumSet.noneOf(Amenity.class);

    protected Stay() {
    }

    // 올린 사람이 방을 나가면 숙소는 남기고 올린 사람만 비운다(그 숙소에 다른 사람 표가 있을 수 있다)
    void forgetAddedBy() {
        addedBy = null;
    }

    public Stay(Trip trip, Member addedBy, String title, String url, String imageUrl, Integer price, Set<Amenity> amenities) {
        this.trip = trip;
        this.addedBy = addedBy;
        this.title = title;
        this.url = url;
        this.imageUrl = imageUrl;
        this.price = price;
        this.amenities.addAll(amenities);
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getImageUrl() { return imageUrl; }
    public Integer getPrice() { return price; }
    public Set<Amenity> getAmenities() { return amenities; }
}
