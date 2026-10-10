package com.nexflow.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 覆盖基类 findAll，顺带抓取 roles / permissions。
     * open-in-view 已关闭，若在外层不再持有会话（如配置中心用户列表直接映射 DTO），
     * 访问惰性集合会抛 LazyInitializationException。
     */
    @Override
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<User> findAll();

    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where u.username = :username")
    Optional<User> findWithRolesByUsername(@Param("username") String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") Long id);

    List<User> findByStatusOrderByIdAsc(UserStatus status);

    List<User> findByDeptIdOrderByIdAsc(Long deptId);

    @Query("select distinct u from User u join u.roles r where r.code = :roleCode "
            + "and u.status = :status and (:deptId is null or u.deptId = :deptId) "
            + "and (:skillTag is null or :skillTag = '' or u.skillTag = :skillTag) order by u.id asc")
    List<User> findCandidates(@Param("deptId") Long deptId, @Param("skillTag") String skillTag,
                              @Param("roleCode") String roleCode, @Param("status") UserStatus status);
}
