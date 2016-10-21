package at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions;

import com.spotify.docker.client.DockerException;

/**
 */
public class CouldNotGetContainerException extends Exception {
    public CouldNotGetContainerException(DockerException e) {
        super(e);
    }
}
