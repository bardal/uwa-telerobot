package au.edu.uwa.mech.web.cloud;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A {@link ParamStore} in memory: dev mode and tests. */
public final class MemoryParamStore implements ParamStore {
    private final Map<String, String> values = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    @Override
    public void put(String name, String value) {
        values.put(name, value);
    }
}
