import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bootstrap for running old Forge (LaunchWrapper) on Java 9+.
 * LaunchWrapper expects getClass().getClassLoader() to be a URLClassLoader,
 * which is only true on Java 8. This wrapper creates a proper
 * URLClassLoader, loads Launch through it so the cast succeeds.
 *
 * Also sets java.class.path so LaunchWrapper can build its own LaunchClassLoader.
 *
 * <p>Usage: java -Dmsf.cpfile=path/to/classpath.txt Java17Bootstrap [mc-args...]
 */
public class Java17Bootstrap {
    public static void main(String[] args) throws Exception {
        String cpFile = System.getProperty("msf.cpfile");
        if (cpFile == null) {
            System.err.println("Missing -Dmsf.cpfile=...");
            System.exit(1);
        }
        List<URL> urls = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(cpFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                for (String path : line.split(File.pathSeparator)) {
                    path = path.trim();
                    if (!path.isEmpty()) {
                        urls.add(new File(path).toURI().toURL());
                    }
                }
            }
        }

        // Also add extra jars from -Dmsf.extra=... (semicolon-separated)
        String extra = System.getProperty("msf.extra");
        if (extra != null) {
            for (String path : extra.split(File.pathSeparator)) {
                path = path.trim();
                if (!path.isEmpty()) {
                    urls.add(new File(path).toURI().toURL());
                }
            }
        }

        System.out.println("[Bootstrap] Loading " + urls.size() + " classpath entries via URLClassLoader");

        // Build the full classpath string and set it as java.class.path
        StringBuilder cpBuilder = new StringBuilder();
        for (URL url : urls) {
            if (cpBuilder.length() > 0) cpBuilder.append(File.pathSeparator);
            cpBuilder.append(new File(url.toURI()).getAbsolutePath());
        }
        System.setProperty("java.class.path", cpBuilder.toString());

        // Verify key jars exist
        String[] keyJars = {"gson", "launchwrapper", "forge-"};
        for (URL url : urls) {
            String s = url.toString();
            for (String key : keyJars) {
                if (s.contains(key)) {
                    File f = new File(url.toURI());
                    System.out.println("[Bootstrap]   " + (f.exists() ? "OK" : "MISSING") + " " + s);
                }
            }
        }

        // Create URLClassLoader with parent=systemClassLoader
        URLClassLoader ucl = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(ucl);

        // Verify gson can be loaded
        try {
            Class<?> gsonClass = ucl.loadClass("com.google.gson.JsonSyntaxException");
            System.out.println("[Bootstrap] Verified: " + gsonClass.getName() + " loaded from " + gsonClass.getProtectionDomain().getCodeSource().getLocation());
        } catch (Exception e) {
            System.err.println("[Bootstrap] WARNING: Cannot load Gson: " + e);
        }

        System.out.println("[Bootstrap] Launching LaunchWrapper...");
        Class<?> mainClass = ucl.loadClass("net.minecraft.launchwrapper.Launch");
        mainClass.getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
