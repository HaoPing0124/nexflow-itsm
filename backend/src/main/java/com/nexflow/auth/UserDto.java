package com.nexflow.auth;

import com.nexflow.user.User;
import com.nexflow.user.UserStatus;
import lombok.Data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class UserDto {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private Long deptId;
    private String deptName;
    private List<String> roles;
    private List<String> permissions;
    private UserStatus status;

    public static UserDto from(User user, String deptName) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setEmail(user.getEmail());
        dto.setDeptId(user.getDeptId());
        dto.setDeptName(deptName);
        dto.setRoles(user.getRoles().stream().map(r -> r.getCode()).sorted().collect(Collectors.toList()));
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .collect(Collectors.toSet());
        dto.setPermissions(permissions.stream().sorted().collect(Collectors.toList()));
        dto.setStatus(user.getStatus());
        return dto;
    }
}
