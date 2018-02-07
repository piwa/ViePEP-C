package at.ac.tuwien.infosys.viepepc.serviceexecutor;

public class ServiceInvokeException extends Exception {
    public ServiceInvokeException(Exception e) {
        super(e);
    }
}