package com.swiftdrop.notification.repository;

import com.swiftdrop.notification.model.Notification;
import com.swiftdrop.notification.model.NotificationChannel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Private to the notification module: other modules use NotificationService. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update Notification n set n.isRead = true, n.readAt = :now where n.userId = :userId and n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    void deleteByUserIdAndId(UUID userId, UUID id);

    void deleteByUserId(UUID userId);

    boolean existsByStatusHistoryIdAndChannel(UUID statusHistoryId, NotificationChannel channel);
}

