package com.swiftdrop.notification.repository;

import com.swiftdrop.notification.model.NotificationPreferences;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Private to the notification module. */
public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {
}

