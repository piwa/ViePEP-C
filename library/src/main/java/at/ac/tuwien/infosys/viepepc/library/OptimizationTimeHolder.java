package at.ac.tuwien.infosys.viepepc.library;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

public class OptimizationTimeHolder {

    public static AtomicLong nextOptimizeTime = new AtomicLong(0);

}
