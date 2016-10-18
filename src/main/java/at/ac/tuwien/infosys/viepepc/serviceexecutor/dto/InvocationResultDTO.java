package at.ac.tuwien.infosys.viepepc.serviceexecutor.dto;

/**
 * User: Philipp Hoenisch
 * Date: 2/10/14
 */

import lombok.Getter;
import lombok.Setter;

/**
 * result of an invocation of webservice. This means this is the result of an exeuction of an webservice.
 * At this moment only the executionTime is interessting and gets stored in the db
 */
@Getter
@Setter
public class InvocationResultDTO {

    /**
     * result of webservice
     */
    private String result;
    /**
     * execution time of webservice
     */
    private long executionTime;
    /**
     * what HTTP status has been returned
     */
    private int status;


}
