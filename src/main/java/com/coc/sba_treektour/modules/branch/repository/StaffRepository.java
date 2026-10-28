package com.coc.sba_treektour.modules.branch.repository;

import com.coc.sba_treektour.modules.branch.entity.Staff;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    @Override
    @EntityGraph(attributePaths = {"branch", "user", "user.role"})
    List<Staff> findAll();

    @EntityGraph(attributePaths = {"branch", "user", "user.role"})
    List<Staff> findByBranchId(Long branchId);

    @EntityGraph(attributePaths = {"branch", "user", "user.role"})
    Optional<Staff> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByBranchId(Long branchId);
}
