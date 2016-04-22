package nl.udev.hellorenderscript.common.algoritm.parameter;

/**
 * Created by ben on 22-4-16.
 */
public class TouchPositionParameter extends AbstractParameter {

    private final TouchHandler user;

    public TouchPositionParameter(String name, TouchHandler user) {
        super(name, ParameterType.TOUCH_POSITION);
        this.user = user;
    }

    public void moved(float xp, float yp) {
        this.user.moved(xp, yp);
    }

    public void released() {
        this.user.released();
    }

    @Override
    public String getDisplayValue() {
        return "N/A";
    }

    public interface TouchHandler {
        void moved(float xp, float yp);
        void released();
    }
}
