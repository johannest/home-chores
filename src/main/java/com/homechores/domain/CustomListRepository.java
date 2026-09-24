package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CustomListRepository extends JpaRepository<CustomList, Long> {

    /** A home's own lists in the order they were made — the order of their tabs. */
    List<CustomList> findByHomeCodeOrderByCreatedAtAscIdAsc(String homeCode);

    long countByHomeCode(String homeCode);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
