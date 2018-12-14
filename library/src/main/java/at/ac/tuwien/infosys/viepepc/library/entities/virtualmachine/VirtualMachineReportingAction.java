package at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine;

import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: sauron
 * Date: 05.02.14
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */
@Entity
@Table(name = "virtual_machine_reporting_action")
@Getter
@Setter
public class VirtualMachineReportingAction implements Serializable {

    /**
     * database identifier
     * important: this identifier is used to identify a vm in the program
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String virtualMachineID;
    private String virtualMachineTypeID;
    private String failureReason = "";

    @Enumerated(EnumType.STRING)
    private Action vmAction;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime timestamp;

    public VirtualMachineReportingAction() {
    }

    public VirtualMachineReportingAction(DateTime date, String vmID, String virtualMachineTypeID, Action action) {
        this.timestamp = date;
        this.virtualMachineID = vmID;
        this.vmAction = action;
        this.virtualMachineTypeID = virtualMachineTypeID;
    }

    public VirtualMachineReportingAction(DateTime date, String vmID, String virtualMachineTypeID, Action action, String failureReason) {
        this.timestamp = date;
        this.virtualMachineID = vmID;
        this.vmAction = action;
        this.virtualMachineTypeID = virtualMachineTypeID;
        this.failureReason = failureReason;
    }

}
