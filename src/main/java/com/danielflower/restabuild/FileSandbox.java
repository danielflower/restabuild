package com.danielflower.restabuild;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class FileSandbox {
    private static final Logger log = LoggerFactory.getLogger(FileSandbox.class);
    public static String dirPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private final File root;

    public FileSandbox(File root) {
        this.root = root;
    }

    public File tempDir(String name) {
        return ensureExists("temp/" + name + "/" + System.currentTimeMillis());
    }
    public File repoDir(String gitUrl) {
        return ensureExists("repos/" + DigestUtils.shaHex(gitUrl));
    }

    private File ensureExists(String relativePath) {
        String path = FilenameUtils.concat(dirPath(root), FilenameUtils.separatorsToSystem(relativePath));
        File f = new File(path);
        log.debug("Created " + dirPath(f) + "? " + f.mkdirs());
        return f;
    }
}
