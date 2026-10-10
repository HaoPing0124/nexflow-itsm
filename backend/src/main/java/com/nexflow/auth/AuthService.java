package com.nexflow.auth;

import com.nexflow.common.BusinessException;
import com.nexflow.common.ErrorCode;
import com.nexflow.config.JwtService;
import com.nexflow.user.Dept;
import com.nexflow.user.DeptRepository;
import com.nexflow.user.User;
import com.nexflow.user.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final DeptRepository deptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        if (username == null || password == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        User user = userService.findByUsername(username.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号已停用，请联系管理员");
        }
        return buildLogin(userService.loadUser(user.getId()));
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少刷新令牌");
        }
        try {
            Claims claims = jwtService.parse(refreshToken);
            if (!"refresh".equals(claims.get("type"))) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "无效的刷新令牌");
            }
            User user = userService.findByUsername(claims.getSubject())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));
            if (!user.isActive()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号已停用，请联系管理员");
            }
            return buildLogin(userService.loadUser(user.getId()));
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌已过期，请重新登录");
        }
    }

    private LoginResponse buildLogin(User user) {
        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).distinct().sorted().toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode()).distinct().sorted().toList();
        String token = jwtService.issueToken(user.getUsername(), user.getId(), roles, permissions);
        String refreshToken = jwtService.issueRefreshToken(user.getUsername(), user.getId());
        String deptName = user.getDeptId() != null
                ? deptRepository.findById(user.getDeptId()).map(Dept::getName).orElse(null) : null;
        return new LoginResponse(token, refreshToken, UserDto.from(user, deptName));
    }
}
