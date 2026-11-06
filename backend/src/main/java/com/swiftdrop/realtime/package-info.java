/**
 * <b>realtime module.</b> Pushes parcel status changes to open browsers over server-sent events
 * (report section 3.7). Consumes {@code ParcelStatusChangedEvent} from its own RabbitMQ queue;
 * owns no tables.
 *
 * <p>Owner: Member 5 (Public Tracking &amp; Real-time Updates). See docs/realtime.md.
 *
 * <p>Pushed messages only keep screens fresh: the database is the true record, and a browser that
 * reconnects reloads the current state over REST.
 *
 * <p>Layout: {@code config} (queue), {@code event} (listener), {@code service} (connection hub),
 * {@code controller} (stream endpoints), {@code dto}.
 */
package com.swiftdrop.realtime;
