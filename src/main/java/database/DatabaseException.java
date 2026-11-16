package database;

import java.io.Serial;

public class DatabaseException extends RuntimeException {

    @Serial private static final long serialVersionUID = -420103154764822555L;

    public DatabaseException(String msg) {
        super(msg);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
