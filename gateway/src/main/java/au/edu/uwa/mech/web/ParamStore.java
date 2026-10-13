package au.edu.uwa.mech.web;

import java.util.Optional;

/** Named string parameters that outlive the process (SSM Parameter Store on AWS, Task 7). */
public interface ParamStore {
    Optional<String> get(String name);

    void put(String name, String value);
}
