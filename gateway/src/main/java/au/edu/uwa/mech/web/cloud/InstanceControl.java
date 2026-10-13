package au.edu.uwa.mech.web.cloud;

/** Stops the machine the gateway runs on (the EC2 instance on AWS; nothing in dev). */
public interface InstanceControl {
    /** Throws an unchecked exception if the stop could not be requested. */
    void stopSelf();
}
