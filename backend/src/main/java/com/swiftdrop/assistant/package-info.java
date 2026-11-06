/**
 * <b>assistant module.</b> The customer-facing AI delivery assistant. Answers questions about
 * tracking, fees and the service by calling tools: public tracking (tracking module) and fee
 * estimates (pricing, through {@link com.swiftdrop.assistant.service.FeeEstimateLookup}).
 * Owns no tables.
 *
 * <p>Owner: Member 5 (Public Tracking &amp; Real-time Updates). See docs/assistant.md.
 *
 * <p>The language model never receives personal data, and message text is never logged.
 *
 * <p>Layout: {@code controller}, {@code service}, {@code tools} (functions the model may call),
 * {@code llm} (model client), {@code dto.request} / {@code dto.response}, {@code config}.
 */
package com.swiftdrop.assistant;
