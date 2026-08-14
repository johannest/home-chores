package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChoreTaskRepository extends JpaRepository<ChoreTask, Long> {

    List<ChoreTask> findByHomeCodeOrderByCreatedAtAsc(String homeCode);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
