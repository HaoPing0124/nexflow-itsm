package com.nexflow.user;

import com.nexflow.ticket.Ticket;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final DeptRepository deptRepository;

    public Specification<Ticket> ticketSpecification(UserPrincipal user) {
        if (user.hasRole("ADMIN")) {
            return null;
        }
        List<Specification<Ticket>> parts = new ArrayList<>();
        if (user.hasRole("REQUESTER")) {
            parts.add((root, query, cb) -> cb.equal(root.get("creatorId"), user.getUserId()));
        }
        if (user.hasRole("AGENT")) {
            parts.add((root, query, cb) -> cb.or(
                    cb.equal(root.get("assigneeId"), user.getUserId()),
                    cb.equal(root.get("groupDeptId"), user.getDeptId())));
        }
        if (user.hasRole("SUPERVISOR")) {
            Set<Long> subtree = deptSubtree(user.getDeptId());
            parts.add((root, query, cb) -> root.get("groupDeptId").in(subtree));
        }
        if (parts.isEmpty()) {
            // 无任何数据域角色，只允许看自己创建的
            return (root, query, cb) -> cb.equal(root.get("creatorId"), user.getUserId());
        }
        Specification<Ticket> result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            Specification<Ticket> part = parts.get(i);
            result = result.or(part);
        }
        return result;
    }

    public boolean canAccess(Ticket ticket, UserPrincipal user) {
        if (user.hasRole("ADMIN")) {
            return true;
        }
        boolean allowed = false;
        if (user.hasRole("REQUESTER")) {
            allowed = allowed || ticket.getCreatorId().equals(user.getUserId());
        }
        if (user.hasRole("AGENT")) {
            allowed = allowed || (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.getUserId()))
                    || (ticket.getGroupDeptId() != null && ticket.getGroupDeptId().equals(user.getDeptId()));
        }
        if (user.hasRole("SUPERVISOR")) {
            allowed = allowed || (ticket.getGroupDeptId() != null
                    && deptSubtree(user.getDeptId()).contains(ticket.getGroupDeptId()));
        }
        return allowed;
    }

    public Set<Long> deptSubtree(Long deptId) {
        Set<Long> result = new HashSet<>();
        if (deptId == null) {
            return result;
        }
        List<Dept> all = deptRepository.findAll();
        result.add(deptId);
        boolean added = true;
        while (added) {
            added = false;
            for (Dept dept : all) {
                if (dept.getParentId() != null && result.contains(dept.getParentId()) && !result.contains(dept.getId())) {
                    result.add(dept.getId());
                    added = true;
                }
            }
        }
        return result;
    }
}
