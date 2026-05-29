import java.io.File;
import java.util.zip.ZipFile;

public class TestZip {
    public static void main(String[] args) throws Exception {
        String path = "C:\\Users\\xjh37\\.gradle\\caches\\minecraftforge\\forgegradle\\mavenizer\\caches\\maven\\forge\\de\\oceanlabs\\mcp\\mcp_config\\1.14.4-20190829.143755\\mcp_config-1.14.4-20190829.143755.zip";
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("File exists: " + new File(path).exists());
        System.out.println("File size: " + new File(path).length());
        try (ZipFile zf = new ZipFile(path)) {
            System.out.println("Entries: " + zf.size());
            zf.stream().limit(5).forEach(e -> System.out.println("  " + e.getName()));
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
