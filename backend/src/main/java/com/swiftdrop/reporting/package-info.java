/**
 * <b>reporting module.</b> Reporting: read-only reports and dashboard data for dispatchers/admins.
 *
 * <p>Owner: TBD (see docs/MODULE_OWNERSHIP.md).
 *
 * <p>Layout:
 * <ul>
 *   <li>{@code controller} - REST controllers (thin: validate, delegate, map to DTO)</li>
 *   <li>{@code service} - business logic and transactions; the only entry point for other modules</li>
 *   <li>{@code repository} - Spring Data JPA repositories (private to this module)</li>
 *   <li>{@code model} - JPA entities and enums</li>
 *   <li>{@code dto.request} / {@code dto.response} - API request and response records</li>
 *   <li>{@code mapper} - entity to DTO mapping</li>
 *   <li>{@code exception} - module-specific exceptions extending common.exception classes</li>
 * </ul>
 *
 * <p>Other modules must use this module's services (or events), never its repositories or entities directly.
 */
package com.swiftdrop.reporting;
