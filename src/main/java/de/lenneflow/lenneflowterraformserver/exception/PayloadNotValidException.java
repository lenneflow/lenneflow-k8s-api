package de.lenneflow.lenneflowterraformserver.exception;

import java.io.Serial;

public class PayloadNotValidException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayloadNotValidException(String msg) {
        super(msg);
    }
}
