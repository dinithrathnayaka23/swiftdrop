/**
 * <b>assistant module.</b> The AI delivery assistant for signed-in customers. Answers questions
 * about tracking, their own recent parcels, fees and the service by calling tools that use the
 * tracking, parcel, pricing and settings modules. Owns no tables.
 *
 * <p>Owner: Member 5 (Public Tracking &amp; Real-time Updates). See docs/assistant.md.
 *
 * <p>The language model never receives personal data, and message text is never logged.
 */
package com.swiftdrop.assistant;
