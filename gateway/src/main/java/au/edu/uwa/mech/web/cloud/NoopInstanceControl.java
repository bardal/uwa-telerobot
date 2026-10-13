package au.edu.uwa.mech.web.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dev mode, local runs and {@code --no-self-stop}: the instance is never stopped. */
public final class NoopInstanceControl implements InstanceControl {
    private static final Logger LOG = LoggerFactory.getLogger(NoopInstanceControl.class);

    @Override
    public void stopSelf() {
        LOG.info("idle: an instance stop would happen now (self-stop is off)");
    }
}
