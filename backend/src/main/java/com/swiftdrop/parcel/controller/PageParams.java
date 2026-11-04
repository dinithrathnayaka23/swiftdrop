package com.swiftdrop.parcel.controller;

import com.swiftdrop.common.exception.BadRequestException;

/** Checks the page/size query parameters of the parcel lists. */
final class PageParams {

    static final int MAX_SIZE = 100;

    private PageParams() {
    }

    /** @throws BadRequestException if page is negative or size is not 1 to {@value #MAX_SIZE} */
    static void check(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
