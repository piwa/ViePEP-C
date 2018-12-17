package at.ac.tuwien.infosys.viepepc.engine;

import at.ac.tuwien.infosys.viepepc.scheduler.core.Reasoning;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Scope("prototype")
@Slf4j
public class ReasoningActivator {

    @Autowired
    protected Reasoning reasoning;

    @Value("${reasoner.autoTerminate}")
    private boolean autoTerminate;

    public Future<Boolean> start() throws Exception {
        return reasoning.runReasoning(new Date(), autoTerminate);
    }

    public void stop() {
        reasoning.stop();
    }
}
