package at.ac.tuwien.infosys.viepepc.library.registry.impl.container;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created by philippwaibel on 07/12/2016.
 */
@ResponseStatus(value= HttpStatus.NOT_FOUND, reason="Container configuration not found in registry")
public class ContainerConfigurationNotFoundException extends Exception {
    private static final String DEFAULT_MESSAGE = "Container configuration not found in registry";

    public ContainerConfigurationNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public ContainerConfigurationNotFoundException(String message) {
        super(message);
    }

}