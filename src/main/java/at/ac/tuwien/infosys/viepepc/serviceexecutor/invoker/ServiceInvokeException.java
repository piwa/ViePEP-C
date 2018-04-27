package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

public class ServiceInvokeException extends Exception {
    public ServiceInvokeException(Exception e) {
        super(e);
    }
}