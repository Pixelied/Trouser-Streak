package org.gradle.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Small auditable wrapper bootstrap used when the canonical Gradle wrapper JAR is unavailable. */
public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path appHome = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path propertiesPath = appHome.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) { properties.load(input); }
        String url = required(properties, "distributionUrl").replace("\\:", ":");
        String expectedSha = required(properties, "distributionSha256Sum").toLowerCase();
        String fileName = Path.of(URI.create(url).getPath()).getFileName().toString();
        String version = fileName.replace("gradle-", "").replace("-bin.zip", "");
        Path cache = Path.of(System.getProperty("user.home"), ".gradle", "wrapper", "manual", "gradle-" + version);
        Path distribution = cache.resolve("gradle-" + version);
        Path executable = distribution.resolve(isWindows() ? "bin/gradle.bat" : "bin/gradle");
        if (!Files.isRegularFile(executable)) install(url, expectedSha, cache, distribution, fileName);

        List<String> command = new ArrayList<>();
        if (isWindows()) { command.add("cmd.exe"); command.add("/d"); command.add("/c"); }
        command.add(executable.toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(appHome.toFile()).inheritIO().start();
        System.exit(process.waitFor());
    }

    private static void install(String url, String expectedSha, Path cache, Path distribution, String fileName) throws Exception {
        Files.createDirectories(cache);
        Path zip = cache.resolve(fileName);
        Path partial = cache.resolve(fileName + ".part");
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "HyperShot-Gradle-Wrapper");
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);
        String actual = sha256(zip);
        if (!actual.equals(expectedSha)) throw new IOException("Gradle checksum mismatch: expected " + expectedSha + ", got " + actual);

        Path extraction = cache.resolve("extract-" + System.nanoTime());
        Files.createDirectories(extraction);
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                Path target = extraction.resolve(entry.getName()).normalize();
                if (!target.startsWith(extraction)) throw new IOException("Unsafe ZIP entry: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path extracted = extraction.resolve(distribution.getFileName());
        Files.move(extracted, distribution, StandardCopyOption.REPLACE_EXISTING);
        deleteRecursively(extraction);
        if (!isWindows()) distribution.resolve("bin/gradle").toFile().setExecutable(true, true);
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
}
