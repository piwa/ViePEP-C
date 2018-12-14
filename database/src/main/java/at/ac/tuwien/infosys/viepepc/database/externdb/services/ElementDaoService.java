package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ElementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 17/05/16.
 */
@Component
@Slf4j
public class ElementDaoService {

    @Autowired
    private ElementRepository elementRepository;

    public void update(Element element) {
        elementRepository.save(element);
    }

}
