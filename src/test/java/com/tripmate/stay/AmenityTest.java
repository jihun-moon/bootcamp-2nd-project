package com.tripmate.stay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmenityTest {

    @Test
    void 숙소_소개_글에서_조건_낱말을_찾는다() {
        assertThat(Amenity.guess("가평 독채 온수풀 · 개별 바베큐, 방 3개 화장실 2개, 노래방 완비"))
                .containsExactlyInAnyOrder(Amenity.DETACHED, Amenity.HEATED_POOL, Amenity.PRIVATE_BBQ,
                        Amenity.ROOMS_3_PLUS, Amenity.BATHS_2_PLUS, Amenity.KARAOKE);
    }

    @Test
    void 방_2개나_화장실_1개는_조건이_아니다() {
        assertThat(Amenity.guess("방 2개, 화장실 1개, 2층 풀빌라")).isEmpty();
        assertThat(Amenity.guess(null)).isEmpty();
    }
}
