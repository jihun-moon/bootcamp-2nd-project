package com.tripmate.role;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HungarianAssignerTest {

    @Test
    void 각자_1등을_주는_것보다_합이_큰_배정을_고른다() {
        // 욕심 방식이면 0번이 10점 칸을 먼저 가져가 합 11. 최적은 0→1, 1→0 으로 합 19
        int[][] score = {{10, 9}, {10, 1}};
        assertThat(HungarianAssigner.assign(score)).containsExactly(1, 0);
    }

    @Test
    void 칸보다_사람이_많으면_점수가_낮은_쪽이_빠진다() {
        int[][] score = {{5}, {7}};
        assertThat(HungarianAssigner.assign(score)).containsExactly(-1, 0);
    }

    @Test
    void 칸이_남으면_비워_둔다() {
        int[][] score = {{1, 3, 2}};
        assertThat(HungarianAssigner.assign(score)).containsExactly(1);
    }

    @Test
    void 금지_칸에는_사람이_남아도_넣지_않는다() {
        int[][] score = {{RoleScorer.FORBIDDEN}, {RoleScorer.FORBIDDEN}};
        assertThat(HungarianAssigner.assign(score)).containsExactly(-1, -1);
    }

    @Test
    void 감점_칸이라도_미배정보다는_낫다() {
        int[][] score = {{-5}};
        assertThat(HungarianAssigner.assign(score)).containsExactly(0);
    }

    @Test
    void 빈_표() {
        assertThat(HungarianAssigner.assign(new int[0][0])).isEmpty();
    }
}
