package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${optimization.values.k}")
    private int kConfig = -1;


    @Value("${default.vm.cores}")
    private int defaultVMCores;

    public void initializeVMs() {
        int V = getVMTypes().size();
        int K = 0;
        if(kConfig == -1) {
            K = getVMTypes().size();
        }
        else {
            K = kConfig;
        }
        try {
            for (int v = 1; v <= V; v++) {
                VMType vmType = getVmTypeFromIdentifier(v);

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

    public VMType getVmTypeFromIdentifier(int identifier) throws Exception {
        for(VMType vmType : getVMTypes()) {
            if(vmType.getIdentifier() == identifier) {
                return vmType;
            }
        }
        throw new Exception("TYPE not found");
    }

    public VMType getVmTypeFromCore(int cores, String location) throws Exception {
        for(VMType vmType : getVMTypes()) {
            if(vmType.getCores() == cores && vmType.getLocation().equals(location)) {
                return vmType;
            }
        }
        throw new Exception("TYPE not found");
    }

    public VMType getDefaultVmType() {
        try {
            return getVmTypeFromCore(defaultVMCores, "internal");
        } catch (Exception e) {
            log.error("EXCEPTION", e);
        }
        return null;
    }

}
