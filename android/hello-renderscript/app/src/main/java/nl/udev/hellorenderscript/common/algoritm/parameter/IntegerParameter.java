package nl.udev.hellorenderscript.common.algoritm.parameter;

/**
 * Integer parameter.
 *
 * Created by ben on 9-2-16.
 */
public class IntegerParameter extends AbstractParameter {

    private final int minValue;
    private final int maxValue;
    private final ParameterUser<Integer> user;
    private int currentValue;

    public IntegerParameter(String name,
                            int min,
                            int max,
                            int initial,
                            ParameterUser<Integer> user) {
        super(name, ParameterType.INTEGER);
        this.minValue = min;
        this.maxValue = max;
        this.user = user;

        setValue(initial);
    }

    public void setValue(int value) {
        if(value > maxValue) {
            throw new IllegalArgumentException("Value out of range " + value + " > " + maxValue);
        }
        if(value < minValue) {
            throw new IllegalArgumentException("Value out of range " + value + " < " + minValue);
        }

        this.currentValue = value;
        this.user.handleValueChanged(this.currentValue);
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public int getCurrentValue() {
        return currentValue;
    }

    @Override
    public String getDisplayValue() {
        return this.user.displayValue(this.currentValue);
    }
}
