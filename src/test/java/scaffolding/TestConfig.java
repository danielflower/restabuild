package scaffolding;

import com.danielflower.restabuild.Config;
import com.danielflower.restabuild.FileSandbox;

import java.io.File;
import java.io.IOException;

public class TestConfig {
    public static final Config config;

    static {
        try {
            config = Config.load(new String[]{"sample-config.properties"});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static FileSandbox testSandbox() {
        return new FileSandbox(new File("target/test-sandbox/" + System.currentTimeMillis()));
    }
}
