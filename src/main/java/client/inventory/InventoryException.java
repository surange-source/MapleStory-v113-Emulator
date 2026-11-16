package client.inventory;

import java.io.Serial;

public class InventoryException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    /** Creates a new instance of InventoryException */
    public InventoryException() {
        super();
    }

    public InventoryException(String msg) {
        super(msg);
    }
}
