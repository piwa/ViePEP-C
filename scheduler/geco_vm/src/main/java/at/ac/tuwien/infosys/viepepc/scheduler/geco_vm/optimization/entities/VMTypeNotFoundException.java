package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities;

public class VMTypeNotFoundException  extends Exception {



    public VMTypeNotFoundException(String message) {
        super(message);
    }

    public VMTypeNotFoundException(Exception e) {
        super(e);
    }
}
