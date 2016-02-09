package nl.udev.hellorenderscript.common.algoritm.parameter;

/**
 * Helper class for parameters.
 *
 * Created by ben on 9-2-16.
 */
public abstract class AbstractParameter {
    private final String name;
    private final ParameterType type;

    protected AbstractParameter(String name, ParameterType type) {
        this.name = name;
        this.type = type;
    }

    /**
     * @return  The display value of the parameter.
     */
    public abstract String getDisplayValue();

    /**
     * @return  The name of the parameter.
     */
    public String getName() {
        return name;
    }

    /**
     * @return  The type of the parameter.
     */
    public ParameterType getType() {
        return type;
    }
}
