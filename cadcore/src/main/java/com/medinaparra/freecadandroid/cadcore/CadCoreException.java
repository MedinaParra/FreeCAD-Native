package com.medinaparra.freecadandroid.cadcore;

public final class CadCoreException extends Exception {
    public final String code;

    public CadCoreException(String code, String message) {
        super(message);
        this.code = code == null ? "CAD_CORE_ERROR" : code;
    }

    public CadCoreException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null ? "CAD_CORE_ERROR" : code;
    }
}
