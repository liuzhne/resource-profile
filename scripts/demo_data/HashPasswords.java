/* Generate independent bcrypt hashes, discard each strong plaintext immediately.
 * Requires JDK21 and spring-security-crypto 6.2.4. The destination is private.
 * Never use shared default passwords for the synthetic demonstration accounts.
 */
import java.security.SecureRandom;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.bcrypt.BCrypt;

class HashPasswords {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("One private output path required");
        int count = 1278;
        String[] hashes = new String[count];
        var pool = Executors.newFixedThreadPool(6);
        for (int i = 0; i < count; i++) {
            final int index = i;
            pool.submit(() -> {
                var random = new SecureRandom();
                byte[] bytes = new byte[32];
                random.nextBytes(bytes);
                hashes[index] = BCrypt.hashpw(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), BCrypt.gensalt(10, random));
            });
        }
        pool.shutdown();
        if (!pool.awaitTermination(8, TimeUnit.MINUTES)) throw new IllegalStateException("Hash generation timeout");
        for (String hash : hashes) if (hash == null) throw new IllegalStateException("Hash generation failed");
        Path path = Path.of(args[0]);
        // Create with restrictive permissions before writing, never truncate an existing file.
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(path, "[\"" + String.join("\",\"", hashes) + "\"]\n");
        System.out.println("fresh_bcrypt_hashes=1278; plaintexts_discarded=true");
    }
}
