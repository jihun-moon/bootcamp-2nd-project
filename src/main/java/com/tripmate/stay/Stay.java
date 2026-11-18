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
    private String url;
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
    public Member getAddedBy() { return addedBy; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getImageUrl() { return imageUrl; }
    public Set<Amenity> getAmenities() { return amenities; }
}
