package com.coc.sba_treektour.modules.branch.repository;

import com.coc.sba_treektour.modules.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
}
