package com.babplanner.backend.dev.demo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoSeedRecordRepository extends JpaRepository<DemoSeedRecord, Long> {

    boolean existsByEntityTypeAndEntityId(String entityType, Long entityId);
}
