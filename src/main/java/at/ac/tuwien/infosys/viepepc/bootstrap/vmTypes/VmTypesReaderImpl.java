package at.ac.tuwien.infosys.viepepc.bootstrap.vmTypes;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Component
public class VmTypesReaderImpl {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Value("${vm.types.path}")
    private String vmTypesPath;

    public void readVMTypes() {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( VirtualMachineTypes.class );
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            File file = Paths.get(ClassLoader.getSystemResource(vmTypesPath).toURI()).toFile();
            VirtualMachineTypes virtualMachineTypes = (VirtualMachineTypes) jaxbUnmarshaller.unmarshal(file);
            inMemoryCache.addAllVMType(virtualMachineTypes.getVmTypes());
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

    }
}
