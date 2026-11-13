package com.swiftdrop.assistant.tools;

import com.swiftdrop.parcel.model.ParcelStatusCode;

/** Plain-words status names, written by the server so the model never invents them. */
final class StatusLabels {

    private StatusLabels() {
    }

    static String label(ParcelStatusCode status) {
        return switch (status) {
            case CREATED -> "Booked";
            case PICKED_UP -> "Picked up";
            case IN_TRANSIT -> "In transit";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            case FAILED -> "Delivery failed";
        };
    }

    static String description(ParcelStatusCode status) {
        return switch (status) {
            case CREATED -> "The parcel is booked and waiting to be picked up.";
            case PICKED_UP -> "A courier has picked up the parcel.";
            case IN_TRANSIT -> "The parcel is on its way to the drop-off city.";
            case OUT_FOR_DELIVERY -> "The parcel is with a courier for delivery today.";
            case DELIVERED -> "The parcel has been delivered.";
            case FAILED -> "The delivery didn't succeed. Please contact us for help.";
        };
    }
}
