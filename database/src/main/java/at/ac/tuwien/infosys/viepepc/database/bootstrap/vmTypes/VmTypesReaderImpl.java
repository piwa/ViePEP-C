package at.ac.tuwien.infosys.viepepc.database.bootstrap.vmTypes;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Component
@Slf4j
public class VmTypesReaderImpl {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Value("${vm.types.path}")
    private String vmTypesPath;

    public void readVMTypes() {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( VirtualMachineTypes.class );
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
//            File file = Paths.get(Objects.requireNonNull(this.getClass().getClassLoader().getResource(vmTypesPath)).toURI()).toFile();
            InputStream file = this.getClass().getResourceAsStream(vmTypesPath);
            VirtualMachineTypes virtualMachineTypes = (VirtualMachineTypes) jaxbUnmarshaller.unmarshal(file);
//            VirtualMachineTypes virtualMachineTypes = (VirtualMachineTypes) jaxbUnmarshaller.unmarshal(new StringReader(xmlString));
            inMemoryCache.getVmTypes().addAll(virtualMachineTypes.getVmTypes());
            inMemoryCache.getVmTypes().sort(Comparator.comparing(VMType::getCores));//.thenComparing(VMType::getRamPoints));
        } catch (JAXBException | NullPointerException e) {
            log.error("Exception", e);
        }

    }
}
