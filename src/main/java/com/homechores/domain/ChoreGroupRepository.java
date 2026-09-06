package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChoreGroupRepository extends JpaRepository<ChoreGroup, Long> {

    /** The home's groups in the order the admin arranged them. Ties break on id rather than
     *  createdAt: groups are made one at a time by hand, so id order is creation order, and
     *  unlike createdAt it can never be null. */
    List<ChoreGroup> findByHomeCodeOrderBySortOrderAscIdAsc(String homeCode);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
