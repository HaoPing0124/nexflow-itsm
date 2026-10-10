package com.nexflow.user;

import com.nexflow.common.BusinessException;
import com.nexflow.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User loadUser(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findWithRolesByUsername(String username) {
        return userRepository.findWithRolesByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User createUser(String username, String password, String displayName, String email,
                           Long deptId, java.util.Set<String> roleCodes, UserStatus status) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setDeptId(deptId);
        user.setStatus(status != null ? status : UserStatus.ACTIVE);
        if (roleCodes != null) {
            roleCodes.forEach(code -> roleRepository.findByCode(code)
                    .ifPresent(r -> user.getRoles().add(r)));
        }
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, String password, String displayName, String email,
                           Long deptId, java.util.Set<String> roleCodes, UserStatus status) {
        User user = loadUser(id);
        if (password != null && !password.isBlank()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (deptId != null) {
            user.setDeptId(deptId);
        }
        if (status != null) {
            user.setStatus(status);
        }
        if (roleCodes != null) {
            user.getRoles().clear();
            roleCodes.forEach(code -> roleRepository.findByCode(code)
                    .ifPresent(r -> user.getRoles().add(r)));
        }
        return userRepository.save(user);
    }
}
