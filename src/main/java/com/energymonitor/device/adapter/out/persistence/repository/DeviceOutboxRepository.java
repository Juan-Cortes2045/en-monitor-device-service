package com.energymonitor.device.adapter.out.persistence.repository;

import com.energymonitor.device.adapter.out.persistence.entity.DeviceOutboxEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository over {@code device_outbox}.
 */
@Repository
public interface DeviceOutboxRepository extends JpaRepository<DeviceOutboxEntity, String> {

    /**
     * The messages still waiting for the broker, oldest first, so the order in which devices
     * changed is the order in which other services hear about it.
     */
    @Query("""
            select m from DeviceOutboxEntity m
            where m.publishedAt is null and m.discardedAt is null
            order by m.occurredAt asc, m.idMessage asc
            """)
    List<DeviceOutboxEntity> findPending(Pageable page);
}
