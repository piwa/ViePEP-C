package at.ac.tuwien.infosys.viepepc.scheduler.geco.onlycontainer;

import lombok.Getter;
import org.uncommons.maths.number.NumberGenerator;

import java.util.Random;


public class DiscreteUniformRangeGenerator  implements NumberGenerator<Integer> {
    private final Random rng;
    @Getter private int maximumValue;
    @Getter private int minimumValue;

    public DiscreteUniformRangeGenerator(int minimumValue, int maximumValue, Random rng) {
        this.rng = rng;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
    }

    public Integer nextValue() {
        return this.rng.nextInt(this.maximumValue + 1 + this.minimumValue) - this.minimumValue;
    }
}