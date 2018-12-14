package at.ac.tuwien.infosys.viepepc.library.registry.impl.container;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created by philippwaibel on 07/12/2016.
 */
@ResponseStatus(value= HttpStatus.NOT_FOUND, reason="Container image not found in registry")
public class ContainerImageNotFoundException extends Exception {
    private static final String DEFAULT_MESSAGE = "Container image not found in registry";

    public ContainerImageNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public ContainerImageNotFoundException(String message) {
        super(message);
    }

}