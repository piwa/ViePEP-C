package at.ac.tuwien.infosys.viepepc.library.registry.impl.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created by philippwaibel on 07/12/2016.
 */
@ResponseStatus(value= HttpStatus.NOT_FOUND, reason="ServiceType not found in registry")
public class ServiceTypeNotFoundException extends Exception {
    private static final String DEFAULT_MESSAGE = "ServiceType not found in registry";

    public ServiceTypeNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public ServiceTypeNotFoundException(String message) {
        super(message);
    }

}