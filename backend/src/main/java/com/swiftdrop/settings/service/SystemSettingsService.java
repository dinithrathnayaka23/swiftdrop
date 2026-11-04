package com.swiftdrop.settings.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.dto.request.UpdateSystemSettingsRequest;
import com.swiftdrop.settings.dto.response.PublicSettingsResponse;
import com.swiftdrop.settings.exception.InvalidSettingException;
import com.swiftdrop.settings.mapper.SystemSettingsMapper;
import com.swiftdrop.settings.model.SystemSettings;
import com.swiftdrop.settings.repository.SystemSettingsRepository;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Public API of the settings module. Other modules read the settings with {@link #current()};
 * the admin reads and saves them through the admin endpoints.
 * <p>
 * Reads are cached for a few seconds because maintenance mode is checked on every request. A save
 * clears the cache once it has committed, so this instance sees the change straight away (other
 * instances within {@link #CACHE_TTL}).
 */
@Slf4j
@Service
public class SystemSettingsService {

    static final Duration CACHE_TTL = Duration.ofSeconds(5);
    static final Set<Integer> ITEMS_PER_PAGE = Set.of(10, 20, 50, 100);

    private final SystemSettingsRepository repository;
    private final Supplier<Instant> clock;
    private volatile Cached cached;

    private record Cached(SystemSettingsView settings, Instant loadedAt) {
    }

    @Autowired
    public SystemSettingsService(SystemSettingsRepository repository) {
        this(repository, Instant::now);
    }

    SystemSettingsService(SystemSettingsRepository repository, Supplier<Instant> clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** The current settings (cached for a few seconds). */
    @Transactional(readOnly = true)
    public SystemSettingsView current() {
        Cached snapshot = cached;
        Instant now = clock.get();
        if (snapshot == null || snapshot.loadedAt().plus(CACHE_TTL).isBefore(now)) {
            snapshot = new Cached(SystemSettingsMapper.toView(load()), now);
            cached = snapshot;
        }
        return snapshot.settings();
    }

    /** True while only the admin may use the system. */
    public boolean maintenanceMode() {
        return current().maintenanceMode();
    }

    /** What anyone may see, without signing in. */
    public PublicSettingsResponse publicSettings() {
        return SystemSettingsMapper.toPublic(current());
    }

    /** The admin's view, always read fresh from the database. */
    @Transactional(readOnly = true)
    public SystemSettingsView forAdmin() {
        return SystemSettingsMapper.toView(load());
    }

    /**
     * Saves the whole settings form.
     *
     * @throws ConflictException       if the settings were saved by someone else since the form loaded
     * @throws InvalidSettingException for an unknown time zone or page size
     */
    @Transactional
    public SystemSettingsView update(UpdateSystemSettingsRequest request, UUID adminId) {
        SystemSettings settings = load();
        if (request.version() != settings.getVersion()) {
            throw changedSinceLoaded();
        }
        String timeZone = validTimeZone(request.timeZone());
        if (!ITEMS_PER_PAGE.contains(request.itemsPerPage())) {
            throw new InvalidSettingException("itemsPerPage", "Choose 10, 20, 50 or 100 items per page");
        }
        boolean maintenanceWasOn = settings.isMaintenanceMode();

        settings.setCompanyName(request.companyName().trim());
        settings.setContactEmail(request.contactEmail().trim().toLowerCase());
        settings.setContactPhone(blankToNull(request.contactPhone()));
        settings.setCompanyAddress(blankToNull(request.companyAddress()));
        settings.setTimeZone(timeZone);
        settings.setDateFormat(request.dateFormat());
        settings.setTimeFormat(request.timeFormat());
        settings.setItemsPerPage(request.itemsPerPage().shortValue());
        settings.setDefaultParcelType(request.defaultParcelType());
        settings.setMaxParcelWeightKg(request.maxParcelWeightKg());
        settings.setMaxParcelDimensionCm(request.maxParcelDimensionCm());
        settings.setFailedDeliveryAction(request.failedDeliveryAction());
        settings.setAutoAssignCourier(request.autoAssignCourier());
        settings.setNotifyFailedDeliveries(request.notifyFailedDeliveries());
        settings.setNotifyNewMerchants(request.notifyNewMerchants());
        settings.setNotifyCourierIssues(request.notifyCourierIssues());
        settings.setDailySummaryReport(request.dailySummaryReport());
        settings.setMaintenanceMode(request.maintenanceMode());
        settings.setUpdatedAt(clock.get());
        settings.setUpdatedByUserId(adminId);

        SystemSettings saved = save(settings);
        log.info("System settings updated by user {}", adminId);
        if (maintenanceWasOn != saved.isMaintenanceMode()) {
            log.warn("Maintenance mode turned {} by user {}", saved.isMaintenanceMode() ? "ON" : "OFF", adminId);
        }
        clearCacheAfterCommit();
        return SystemSettingsMapper.toView(saved);
    }

    /**
     * Saves and bumps the version. Another save can still commit between the version check and
     * this one; the {@code @Version} column then stops ours, which must read as a conflict (409),
     * not as the database being unavailable (503).
     */
    private SystemSettings save(SystemSettings settings) {
        try {
            return repository.saveAndFlush(settings);
        } catch (OptimisticLockingFailureException ex) {
            throw changedSinceLoaded();
        }
    }

    private static ConflictException changedSinceLoaded() {
        return new ConflictException(
                "These settings were changed since you opened them. Reload to see the latest values.");
    }

    private SystemSettings load() {
        return repository.findById(SystemSettings.ID)
                .orElseThrow(() -> new IllegalStateException("system_settings row is missing (migration V1_001)"));
    }

    private static String validTimeZone(String value) {
        try {
            return ZoneId.of(value.trim()).getId();
        } catch (DateTimeException ex) {
            throw new InvalidSettingException("timeZone", "Choose a valid time zone");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void clearCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cached = null;
                }
            });
        } else {
            cached = null;
        }
    }
}
