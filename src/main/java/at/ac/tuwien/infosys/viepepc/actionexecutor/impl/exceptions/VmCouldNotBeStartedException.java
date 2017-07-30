package at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions;

public class VmCouldNotBeStartedException extends Exception {


    public VmCouldNotBeStartedException(String message) {
        super(message);
    }

    public VmCouldNotBeStartedException(Exception e) {
        super(e);
    }
}