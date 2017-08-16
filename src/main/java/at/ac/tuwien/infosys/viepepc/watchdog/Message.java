package at.ac.tuwien.infosys.viepepc.watchdog;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Message implements Serializable {

    private Long processStepId;
    private ServiceExecutionStatus status;
    private String body;


    @Override
    public String toString() {
        return "Message{" +
                "processStepId=" + processStepId +
                ", status=" + status +
                ", body='" + body + '\'' +
                '}';
    }
}
