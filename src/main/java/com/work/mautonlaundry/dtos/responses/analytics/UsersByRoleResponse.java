package com.work.mautonlaundry.dtos.responses.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsersByRoleResponse {
    private List<RoleCount> roles;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleCount {
        private String role;
        private long count;
    }
}
