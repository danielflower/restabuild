package com.danielflower.restabuild;

import com.danielflower.restabuild.build.RestaBuildException;
import com.danielflower.restabuild.build.InvalidConfigException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class Config {
    static final String SERVER_PORT = "restabuild.port";
    public static final String DATA_DIR = "restabuild.data";
    public static final String CONTEXT = "restabuild.context";
    public static final String CONCURRENT_BUILDS = "restabuild.concurrent.builds";
    public static final String TIMEOUT = "restabuild.timeout";

    public static Config load(String[] commandLineArgs) throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        for (String key : System.getProperties().stringPropertyNames()) {
            String value = System.getProperty(key);
            env.put(key, value);
        }
        for (String commandLineArg : commandLineArgs) {
            File file = new File(commandLineArg);
            if (file.isFile()) {
                Properties props = new Properties();
                try (FileInputStream inStream = new FileInputStream(file)) {
                    props.load(inStream);
                }
                for (String key : props.stringPropertyNames()) {
                    env.put(key, props.getProperty(key));
                }
            }
        }
        return new Config(env);
    }

    private final Map<String, String> raw;

    private Config(Map<String, String> raw) {
        this.raw = raw;
    }

    public String get(String name, String defaultVal) {
        return raw.getOrDefault(name, defaultVal);
    }

    private String get(String name) {
        String s = get(name, null);
        if (s == null) {
            throw new InvalidConfigException("Missing config item: " + name);
        }
        return s;
    }

    public int getInt(String name) {
        String s = get(name);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new InvalidConfigException("Could not convert " + name + "=" + s + " to an integer");
        }
    }

    public int getInt(String name, int defaultValue) {
        String s = get(name, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new InvalidConfigException("Could not convert " + name + "=" + s + " to an integer");
        }
    }


    public File getOrCreateDir(String name) {
        File f = new File(get(name));
        try {
            FileUtils.forceMkdir(f);
        } catch (IOException e) {
            throw new RestaBuildException("Could not create " + dirPath(f));
        }
        return f;
    }

}

