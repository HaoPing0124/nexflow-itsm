package com.nexflow.auth;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.CurrentUser;
import com.nexflow.user.Dept;
import com.nexflow.user.DeptRepository;
import com.nexflow.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final DeptRepository deptRepository;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        UserPrincipal principal = CurrentUser.get();
        String deptName = principal.getDeptId() != null
                ? deptRepository.findById(principal.getDeptId()).map(Dept::getName).orElse(null) : null;
        UserDto dto = new UserDto();
        dto.setId(principal.getUserId());
        dto.setUsername(principal.getUsername());
        dto.setDisplayName(principal.getDisplayName());
        dto.setEmail(principal.getEmail());
        dto.setDeptId(principal.getDeptId());
        dto.setDeptName(deptName);
        dto.setRoles(principal.getRoles().stream().sorted().toList());
        dto.setPermissions(principal.getPermissions().stream().sorted().toList());
        return ApiResponse.ok(dto);
    }
}
