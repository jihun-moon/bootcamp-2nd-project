package com.tripmate.service.role;

import java.util.Arrays;

// 사람×칸 점수표에서 점수 합이 가장 큰 1:1 배정을 찾는다(헝가리안, O(n^3)).
// 하나씩 1등 역할을 주는 욕심 방식은 두 사람이 같은 역할을 원할 때 전체 합이 나빠져서 이 방식을 쓴다
final class HungarianAssigner {

    // 사람이 "미배정"으로 빠질 때의 점수. 보통 감점(-5 등)보다 훨씬 나빠 웬만하면 누구나 역할을 받지만,
    // 금지 칸(RoleScorer.FORBIDDEN)보다는 나아서 차 없는 사람이 운전에 억지로 들어가지 않는다
    static final int UNASSIGNED = -500;

    private HungarianAssigner() {
    }

    // score[사람][칸] -> 사람마다 고른 칸 번호, 미배정이면 -1
    static int[] assign(int[][] score) {
        var rows = score.length;
        var cols = rows == 0 ? 0 : score[0].length;
        // 정사각형 rows+cols 로 푼다. 사람마다 미배정 열, 칸마다 빈 사람 행이 하나씩 생겨
        // 사람은 늘 빠질 수 있고(UNASSIGNED), 칸은 늘 비어 있을 수 있다(0점)
        var n = rows + cols;

        var u = new long[n + 1];
        var v = new long[n + 1];
        var p = new int[n + 1];
        var way = new int[n + 1];

        for (var i = 1; i <= n; i++) {
            p[0] = i;
            var j0 = 0;
            var minv = new long[n + 1];
            Arrays.fill(minv, Long.MAX_VALUE);
            var used = new boolean[n + 1];
            do {
                used[j0] = true;
                int i0 = p[j0], j1 = 0;
                var delta = Long.MAX_VALUE;
                for (var j = 1; j <= n; j++) {
                    if (used[j]) continue;
                    var cur = cost(score, rows, cols, i0 - 1, j - 1) - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (var j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                var j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        var result = new int[rows];
        Arrays.fill(result, -1);
        for (var j = 1; j <= n; j++) {
            int i = p[j] - 1, c = j - 1;
            if (i < rows && c < cols) result[i] = c;
        }
        return result;
    }

    // 최소 비용 알고리즘이라 점수를 뒤집어 넣는다
    private static long cost(int[][] score, int rows, int cols, int i, int j) {
        if (i >= rows) return 0;
        return (j < cols) ? -score[i][j] : -UNASSIGNED;
    }
}
