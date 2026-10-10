package com.nexflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeptRepository extends JpaRepository<Dept, Long> {

    List<Dept> findAllByOrderBySortOrderAsc();

    List<Dept> findByParentId(Long parentId);
}
