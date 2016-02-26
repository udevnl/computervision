package nl.udev.hellorenderscript.common.algoritm.parameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parameter with limited possible values.
 *
 * Created by ben on 26-2-16.
 */
public class LimitedSettingsParameter<V> extends AbstractParameter {

    private final List<V> options;
    private final ParameterUser<V> user;
    private V currentValue;

    public LimitedSettingsParameter(String name,
                                    List<V> options,
                                    V initial,
                                    ParameterUser<V> user) {
        super(name, ParameterType.LIMITED_SETTINGS);
        this.options = options;
        this.user = user;

        setValue(initial);
    }

    public LimitedSettingsParameter(String name,
                                    V options[],
                                    V initial,
                                    ParameterUser<V> user) {
        this(name, Arrays.asList(options), initial, user);
    }

    /**
     * Set the desired value.
     *
     * Note that the value must be one of the possible (allowed) values or
     * an IllegalArgumentException is given to you.
     *
     * @param desiredValue    The value to set.
     */
    public void setValue(V desiredValue) {
        if(!options.contains(desiredValue)) {
            throw new IllegalArgumentException(
                    "Given value is not a valid option: " + desiredValue +
                            ", valid options: " + Arrays.toString(options.toArray()));
        }

        this.currentValue = desiredValue;
        this.user.handleValueChanged(desiredValue);
    }

    /**
     * @return  All possible values that this parameter can have.
     */
    public List<V> getPossibleValues() {
        return new ArrayList<>(options);
    }

    /**
     * @return  The currently set value
     */
    public V getCurrentValue() {
        return currentValue;
    }

    @Override
    public String getDisplayValue() {
        return this.user.displayValue(this.currentValue);
    }
}
