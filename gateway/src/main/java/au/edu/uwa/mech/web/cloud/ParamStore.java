package au.edu.uwa.mech.web.cloud;

import java.util.Optional;

/** Named string parameters that outlive the process: SSM Parameter Store on AWS, memory in dev. */
public interface ParamStore {
    Optional<String> get(String name);

    void put(String name, String value);
}
