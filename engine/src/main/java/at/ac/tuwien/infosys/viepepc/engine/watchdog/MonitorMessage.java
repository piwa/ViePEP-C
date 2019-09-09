package at.ac.tuwien.infosys.viepepc.engine.watchdog;

import lombok.Data;

import java.io.Serializable;

@Data
public class MonitorMessage implements Serializable {

    private Long totalMemoryUsage;
    private Long totalCpuUsage;

}
