/**
 * <b>delivery module.</b> The courier app: a courier's deliveries for today, delivery details,
 * status updates, proof of delivery and failed-delivery reasons.
 *
 * <p>Owner: Member 7 (see docs/MODULE_OWNERSHIP.md).
 *
 * <p>Talks to other modules only through their public APIs:
 * <ul>
 *   <li>{@code dispatch.api.DispatchApi} - which parcels are assigned to the courier (ownership)</li>
 *   <li>{@code parcel.service.ParcelService} - reading parcels</li>
 *   <li>{@code parcel.service.ParcelStatusService} - status changes and status history</li>
 * </ul>
 * A courier only ever sees the parcels assigned to them; any other parcel is reported as not found.
 *
 * <p>Layout:
 * <ul>
 *   <li>{@code controller} - REST controllers (thin: validate, delegate, map to DTO)</li>
 *   <li>{@code service} - business logic and transactions; the only entry point for other modules</li>
 *   <li>{@code repository} - Spring Data JPA repositories (private to this module)</li>
 *   <li>{@code model} - JPA entities and enums</li>
 *   <li>{@code dto.request} / {@code dto.response} - API request and response records</li>
 *   <li>{@code mapper} - parcel data to DTO mapping</li>
 *   <li>{@code exception} - module-specific exceptions extending common.exception classes</li>
 *   <li>{@code event} - RabbitMQ publishers/listeners for this module</li>
 * </ul>
 */
package com.swiftdrop.delivery;
