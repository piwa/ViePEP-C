package at.ac.tuwien.infosys.viepepc.database.inmemory.services;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 10/06/16. modified by Gerta Sheganaku
 */
@Slf4j
@Component
public class CacheVirtualMachineService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    public List<VMType> getVMTypes() {
        return inMemoryCache.getVmTypes();
    }

    public VMType getDefaultVMType() throws Exception {

        for (VMType vmType : getVMTypes()) {
            if (vmType.getCores() == 4) {
                return vmType;
            }
        }
        throw new Exception("TYPE not found");
    }

    public Map<UUID, VirtualMachineInstance> getAllVMInstancesFromInMemory() {
        return inMemoryCache.getVmInstances();
    }

    public List<VirtualMachineInstance> getDeployedVMInstances() {
        return getAllVMInstancesFromInMemory().values().stream().filter(vm -> vm.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)).collect(Collectors.toList());
    }

    public List<VirtualMachineInstance> getDeployingVMInstances() {
        return getAllVMInstancesFromInMemory().values().stream().filter(vm -> vm.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYING)).collect(Collectors.toList());
    }

    public List<VirtualMachineInstance> getScheduledVMInstances() {
        return getAllVMInstancesFromInMemory().values().stream().filter(vm -> vm.getVirtualMachineStatus().equals(VirtualMachineStatus.SCHEDULED)).collect(Collectors.toList());
    }

    public List<VirtualMachineInstance> getDeployingAndDeployedVMInstances() {
        return getAllVMInstancesFromInMemory().values().stream().filter(vm -> vm.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYING) ||
                vm.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)).collect(Collectors.toList());
    }

    public List<VirtualMachineInstance> getScheduledAndDeployingAndDeployedVMInstances() {
        return getAllVMInstancesFromInMemory().values().stream().filter(vm -> vm.getVirtualMachineStatus().equals(VirtualMachineStatus.SCHEDULED) ||
                vm.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYING) ||
                vm.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)).collect(Collectors.toList());
    }

    public VirtualMachineInstance getNewVirtualMachineInstance(int cores) throws Exception {

        for (VMType vmType : getVMTypes()) {
            if (vmType.getCores() == cores) {
                return new VirtualMachineInstance(vmType);
            }
        }
        throw new Exception("TYPE not found");
    }
//
//    public ConcurrentMap<VirtualMachineInstance, Object> getVmDeployedWaitObjectMap() {
//        return inMemoryCache.getVmDeployedWaitObjectMap();
//    }
}
