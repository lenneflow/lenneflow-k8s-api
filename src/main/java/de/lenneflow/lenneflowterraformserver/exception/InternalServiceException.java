package de.lenneflow.lenneflowterraformserver.exception;

import java.io.Serial;

public class InternalServiceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
    public InternalServiceException(String msg) {
        super(msg);
    }
}
