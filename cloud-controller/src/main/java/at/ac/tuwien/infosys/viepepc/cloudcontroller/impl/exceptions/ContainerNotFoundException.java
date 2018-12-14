package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions;

/**
 *
 */
public class ContainerNotFoundException extends Exception {
    public ContainerNotFoundException(String s) {
        super(s);
    }

    public ContainerNotFoundException(Exception s) {
        super(s);
    }
}
