package com.swiftdrop.settings.model;

/** How dates are shown in the web app. Column {@code system_settings.date_format}. */
public enum DateFormat {
    /** 24 Aug 2026 (default; as in the UI designs) */
    DD_MMM_YYYY,
    /** 24/08/2026 */
    DD_MM_YYYY,
    /** 08/24/2026 */
    MM_DD_YYYY,
    /** 2026-08-24 */
    YYYY_MM_DD
}
