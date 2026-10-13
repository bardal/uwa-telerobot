package au.edu.uwa.mech.web.archive;

import java.util.Optional;

/** Where archive objects live: a directory in dev and tests, an S3 bucket in production. */
public interface ArchiveStore {
    /** Writes (or overwrites) an object. Throws an unchecked exception on failure. */
    void put(String key, byte[] body, String contentType);

    Optional<byte[]> get(String key);

    /** Removes an object; a missing key is not an error. */
    void delete(String key);

    /** Total bytes stored, used for the archive cap. */
    long bytesUsed();
}
