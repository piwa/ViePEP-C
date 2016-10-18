package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by philippwaibel on 10/06/16. modified by Gerta Sheganaku
 */
@Component
@Slf4j
public class CacheVirtualMachineService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    private int V = 3; //Available types of VM's
    private int K = 3;

    public void initializeVMs() {
        try {
            for (int v = 1; v <= V; v++) {
                VMType vmType = VMType.fromIdentifier(v);

                for (int k = 1; k <= K; k++) {
                    inMemoryCache.addVirtualMachine(new VirtualMachine(v + "_" + k, vmType));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Set<VMType> getVMTypes() {
        return inMemoryCache.getVmTypeVmMap().keySet();
    }

    public List<VirtualMachine> getVMs(VMType vmType) {
        return inMemoryCache.getVmTypeVmMap().get(vmType);
    }

    public List<VirtualMachine> getAllVMs() {
        List<VirtualMachine> allVMs = new ArrayList<VirtualMachine>();
        for (VMType vmType : getVMTypes()) {
            allVMs.addAll(getVMs(vmType));
        }
        return allVMs;
    }

    public Map<VMType, List<VirtualMachine>> getVMMap() {
        return inMemoryCache.getVmTypeVmMap();
    }

    public VirtualMachine getVMById(int v, int k) {
        for (VirtualMachine virtualMachine : getAllVMs()) {
            if (virtualMachine.getName().equals(v + "_" + k)) {
                return virtualMachine;
            }
        }
        return null;
    }

    public Set<VirtualMachine> getStartedVMs() {
        Set<VirtualMachine> result = new HashSet<VirtualMachine>();
        for (VirtualMachine vm : getAllVMs()) {
            if (vm.isStarted()) {
                result.add(vm);
            }
        }
        return result;
    }

    public Set<VirtualMachine> getScheduledForStartVMs() {
        Set<VirtualMachine> result = new HashSet<VirtualMachine>();
        for (VirtualMachine vm : getAllVMs()) {
            if (vm.getToBeTerminatedAt() != null) {
                result.add(vm);
            }
        }
        return result;
    }

    public Set<VirtualMachine> getStartedAndScheduledForStartVMs() {
        Set<VirtualMachine> result = new HashSet<VirtualMachine>();
        result.addAll(getStartedVMs());
        result.addAll(getScheduledForStartVMs());
        return result;
    }

}
