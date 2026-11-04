package com.swiftdrop.settings.repository;

import com.swiftdrop.settings.model.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** Private to the settings module: other modules go through SystemSettingsService. */
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Short> {
}
