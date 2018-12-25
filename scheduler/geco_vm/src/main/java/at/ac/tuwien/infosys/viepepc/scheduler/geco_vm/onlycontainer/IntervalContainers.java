package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import lombok.Data;
import org.joda.time.Interval;

import java.util.List;

@Data
public class IntervalContainers {

    private Interval interval;
    private List<Container> containers;

}
