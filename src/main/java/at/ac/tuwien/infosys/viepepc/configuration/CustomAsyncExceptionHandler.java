package at.ac.tuwien.infosys.viepepc.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

/**
 * Created by philippwaibel on 13/07/2017.
 */
@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... obj) {

        log.error("Exception message - " + throwable.getMessage());
        log.error("Method name - " + method.getName());
        for (Object param : obj) {
            log.error("Parameter value - " + param);
        }
    }
}
