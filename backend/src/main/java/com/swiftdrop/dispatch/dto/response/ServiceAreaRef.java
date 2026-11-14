package com.swiftdrop.dispatch.dto.response;

import java.util.UUID;

/** A service area (delivery zone) a courier covers, with its name from the config module. */
public record ServiceAreaRef(UUID id, String name) {
}
