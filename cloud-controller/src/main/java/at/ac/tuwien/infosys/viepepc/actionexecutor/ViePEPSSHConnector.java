package at.ac.tuwien.infosys.viepepc.actionexecutor;


import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;

/**
 *
 */
public interface ViePEPSSHConnector {
    /**
     * @param vm      to execute the command on
     * @param command the shell command to be executed
     * @return an string array
     * * [0] the result value
     * * [1] the error result value
     * @throws Exception
     */
    String[] execSSHCommand(VirtualMachine vm, String command) throws Exception;

    void initialize();

    void loadSettings();
}
