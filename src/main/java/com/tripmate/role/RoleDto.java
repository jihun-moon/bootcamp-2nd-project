package com.tripmate.role;

import java.util.List;

public final class RoleDto {

    private RoleDto() {
    }

    // { "roleId": 4, "version": 0 }   roleId null = 미배정으로, version null = 지금 미배정인 사람
    public record MoveRequest(Long roleId, Long version) {
    }

    // roles.js 가 roles 와 assignments 를 따로 받아 칸을 그린다. 미배정은 참여자 목록에서 뺀 나머지
    public record Board(List<RoleView> roles, List<AssignmentView> assignments) {

        static Board of(List<Role> roles, List<Assignment> assignments) {
            return new Board(roles.stream().map(RoleView::from).toList(),
                    assignments.stream().map(AssignmentView::from).toList());
        }
    }

    public record RoleView(Long id, String title, boolean required, int capacity, List<String> tasks) {

        static RoleView from(Role r) {
            return new RoleView(r.getId(), r.getTitle(), r.isRequired(), r.getCapacity(), List.copyOf(r.getTasks()));
        }
    }

    public record AssignmentView(Long roleId, Long memberId, List<String> reasons, Long version) {

        static AssignmentView from(Assignment a) {
            return new AssignmentView(a.getRole().getId(), a.getMember().getId(), List.copyOf(a.getReasons()), a.getVersion());
        }
    }
}
