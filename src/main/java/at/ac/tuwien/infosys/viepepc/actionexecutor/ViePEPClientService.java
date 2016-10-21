package at.ac.tuwien.infosys.viepepc.actionexecutor;

import java.util.List;

public interface ViePEPClientService {
    /**
     * @param instanceId to be terminated
     * if service is not found, the command is just ignored
     */
    void terminateInstanceByID(String instanceId);

    /**
     * @param localeAddress of a VM to be terminated
     * @return true if successful
     */
    boolean terminateInstanceByIP(String localeAddress);


    /**
     * @return a list of active server ips
     */
    List<String> getServerIpList();

    /**
     * starts a new VM with a location preference
     *
     * @param name          of the new VM
     * @param flavorName    the VM flavor
     * @param serviceName   the service which should be deployed on default
     * @param location      location/type of the data center
     * @return              the address of newly started VM
     */
    String startNewVM(String name, String flavorName, String serviceName, String location);

    /**
     * @param private IP
     * @return true if vm is running, otherwise false
     */
    boolean isVMRunning(String vmID);
}
