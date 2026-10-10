package com.nexflow.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * 覆盖基类 findAll，顺带抓取 permissions。
     * 配置中心角色列表在会话外映射 DTO，惰性集合会抛 LazyInitializationException。
     */
    @Override
    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAll();

    Optional<Role> findByCode(String code);
}
