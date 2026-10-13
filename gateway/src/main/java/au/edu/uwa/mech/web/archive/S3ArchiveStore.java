package au.edu.uwa.mech.web.archive;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Archive store on an S3 bucket. No ACLs are set (the bucket enforces owner).
 *
 * <p>{@link #bytesUsed()} is an in-memory counter seeded from SSM {@code /telerobot/archive-bytes}
 * through the injected seed supplier. It moves on every put and delete; the caller persists it by
 * calling {@link #persist()} from a timer (the injected consumer writes the SSM parameter).
 * Mutable keys (indexes, audit files) are measured with a HEAD before an overwrite so repeated
 * flushes do not inflate the counter.
 */
public final class S3ArchiveStore implements ArchiveStore {
    static final String IMMUTABLE = "public, max-age=31536000, immutable";
    static final String INDEX = "public, max-age=30";
    static final String PRIVATE = "private, no-store";

    private final S3Client s3;
    private final String bucket;
    private final AtomicLong bytes;
    private final LongConsumer persist;
    private final AtomicBoolean dirty = new AtomicBoolean();

    public S3ArchiveStore(S3Client s3, String bucket, LongSupplier seed, LongConsumer persist) {
        this.s3 = s3;
        this.bucket = bucket;
        this.bytes = new AtomicLong(Math.max(0, seed.getAsLong()));
        this.persist = persist;
    }

    /** Production factory: region from AWS_REGION (default eu-west-2), URL-connection HTTP client. */
    public static S3ArchiveStore create(String bucket, LongSupplier seed, LongConsumer persist) {
        String r = System.getenv("AWS_REGION");
        if (r == null || r.isBlank()) r = System.getenv("AWS_DEFAULT_REGION");
        if (r == null || r.isBlank()) r = "eu-west-2";
        S3Client c = S3Client.builder()
                .region(Region.of(r))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        return new S3ArchiveStore(c, bucket, seed, persist);
    }

    static String cacheControl(String key, String contentType) {
        if (key.startsWith("private/")) return PRIVATE;
        if ("image/jpeg".equals(contentType)) return IMMUTABLE;
        return INDEX;
    }

    private long sizeOf(String key) {
        try {
            Long n = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength();
            return n == null ? 0 : n;
        } catch (NoSuchKeyException e) {
            return 0;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return 0;
            throw e;
        }
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
        long old = key.endsWith(".jpg") ? 0 : sizeOf(key);
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .cacheControl(cacheControl(key, contentType))
                .build(), RequestBody.fromBytes(body));
        bytes.updateAndGet(b -> Math.max(0, b + body.length - old));
        dirty.set(true);
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        long old = sizeOf(key);
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        if (old > 0) {
            bytes.updateAndGet(b -> Math.max(0, b - old));
            dirty.set(true);
        }
    }

    @Override
    public long bytesUsed() {
        return bytes.get();
    }

    /** Hands the counter to the persist consumer if it changed since the last call. */
    public void persist() {
        if (dirty.getAndSet(false)) {
            try {
                persist.accept(bytes.get());
            } catch (RuntimeException e) {
                dirty.set(true);
                throw e;
            }
        }
    }
}
