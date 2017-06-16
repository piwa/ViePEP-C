package at.ac.tuwien.infosys.viepepc.database.entities.container;

import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: sauron
 * Date: 05.02.14
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */
@Entity
@Table(name = "container_reporting_action")
@Getter
@Setter
public class ContainerReportingAction implements Serializable {

    /**
     * database identifier
     * important: this identifier is used to identify a vm in the program
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String containerID;
    private String virtualMachineID;

    @Enumerated(EnumType.STRING)
    private Action dockerAction;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime timestamp;

    public ContainerReportingAction() {
    }

    public ContainerReportingAction(DateTime date, String containerID, String vmID, Action action) {
        this.timestamp = date;
        this.containerID = containerID;
        this.virtualMachineID = vmID;
        this.dockerAction = action;
    }


}
