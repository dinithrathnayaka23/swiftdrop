package com.swiftdrop.assistant.tools;

/** A tool argument is missing or not valid. The registry turns it into an INVALID_INPUT result. */
public class InvalidToolArgumentException extends RuntimeException {

    private final String field;

    public InvalidToolArgumentException(String field, String problem) {
        super(field + " " + problem);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
