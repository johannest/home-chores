package com.homechores.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface SharedDeviceRepository extends JpaRepository<SharedDevice, Long> {

    List<SharedDevice> findByHomeCodeOrderByPairedAtAsc(String homeCode);

    long countByHomeCode(String homeCode);

    boolean existsByHomeCode(String homeCode);

    @Transactional
    void deleteByHomeCode(String homeCode);
}
