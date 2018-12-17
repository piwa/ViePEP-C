package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 10/06/16. modified by Gerta Sheganaku
 */
@Slf4j
@Component
public class CacheVirtualMachineService {

    @Value("${default.vm.location}")
    private String defaultVMLocation;
    @Value("${default.vm.cores}")
    private int defaultVMCores;
    @Value("${vm.amount.per.type}")
    private int vmAmountPerType = 10;

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    public void initializeVMs() {
        try {
            for (int v = 1; v <= getVMTypes().size(); v++) {
                VMType vmType = getVmTypeFromIdentifier(v);

                for (int k = 1; k <= vmAmountPerType; k++) {
                    inMemoryCache.addVirtualMachine(new VirtualMachine(v + "_" + k, vmType));
                }
            }
        } catch (Exception e) {
            log.error("Exception", e);
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
        getVMTypes().stream().map(this::getVMs).forEach(allVMs::addAll);
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

    public Set<VirtualMachine> getAllVMsFromLocation(String location) {
        return getAllVMs().stream().filter(vm -> vm.getLocation().equals(location)).collect(Collectors.toSet());
    }

    public Set<VirtualMachine> getStartedVMs() {
        return getAllVMs().stream().filter(VirtualMachine::isStarted).collect(Collectors.toSet());
    }

    public Set<VirtualMachine> getScheduledForStartVMs() {
        return getAllVMs().stream().filter(vm -> vm.getToBeTerminatedAt() != null).collect(Collectors.toSet());
    }

    public Set<VirtualMachine> getStartedAndScheduledForStartVMs() {
        Set<VirtualMachine> result = new HashSet<VirtualMachine>();
        result.addAll(getStartedVMs());
        result.addAll(getScheduledForStartVMs());
        return result;
    }

    public VMType getVmTypeFromIdentifier(int identifier) throws Exception {
        for (VMType vmType : getVMTypes()) {
            if (vmType.getIdentifier() == identifier) {
                return vmType;
            }
        }
        throw new Exception("TYPE not found");
    }

    public VMType getVmTypeFromCore(int cores, String location) throws Exception {
        for (VMType vmType : getVMTypes()) {
            if (vmType.getCores() == cores && vmType.getLocation().equals(location)) {
                return vmType;
            }
        }
        throw new Exception("TYPE not found");
    }

    public VMType getDefaultVmType() {
        try {
            return getVmTypeFromCore(defaultVMCores, defaultVMLocation);
        } catch (Exception e) {
            log.error("EXCEPTION", e);
        }
        return null;
    }

}
