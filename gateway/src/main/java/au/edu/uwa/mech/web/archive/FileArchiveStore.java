package au.edu.uwa.mech.web.archive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Archive store on a local directory, for dev mode and tests. */
public final class FileArchiveStore implements ArchiveStore {
    private static final Pattern KEY = Pattern.compile("^[a-z0-9/_.-]+$");
    private final Path root;

    public FileArchiveStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches() || key.contains("..")
                || key.startsWith("/") || key.contains("//")) {
            throw new IllegalArgumentException("bad archive key: " + key);
        }
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("bad archive key: " + key);
        return p;
    }

    @Override
    public synchronized void put(String key, byte[] body, String contentType) {
        Path p = resolve(key);
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.write(tmp, body);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized Optional<byte[]> get(String key) {
        Path p = resolve(key);
        try {
            return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized long bytesUsed() {
        if (!Files.isDirectory(root)) return 0;
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
