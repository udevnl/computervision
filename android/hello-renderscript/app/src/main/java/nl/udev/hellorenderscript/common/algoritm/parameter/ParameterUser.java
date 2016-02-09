package nl.udev.hellorenderscript.common.algoritm.parameter;

/**
 * Interface for users of parameters.
 *
 * Created by ben on 9-2-16.
 */
public interface ParameterUser<P> {

    /**
     * Give the displayed version of the given value.
     *
     * @param value     The value to display
     * @return          The displayed form of the value
     */
    String displayValue(P value);

    /**
     * Handle a change in parameter value.
     *
     * @param newValue    The new changed value of the parameter.
     */
    void handleValueChanged(P newValue);
}
