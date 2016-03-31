package scaffolding;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.danielflower.restabuild.FileSandbox.dirPath;
import static org.apache.commons.io.FilenameUtils.separatorsToSystem;

public class Photocopier {

    public static File sampleDir() {
        return new File(separatorsToSystem("sample-apps"));
    }

    public static File copySampleAppToTempDir(String sampleAppName) {
        String pathname = FilenameUtils.concat(dirPath(sampleDir()), sampleAppName);
        File source = new File(pathname);
        if (!source.isDirectory()) {
            source = new File(separatorsToSystem("../") + pathname);
        }
        if (!source.isDirectory()) {
            throw new RuntimeException("Could not find module " + sampleAppName + " at " + new File(pathname) + " nor " + dirPath(source));
        }

        File target = folderForSampleProject(sampleAppName);
        try {
            FileUtils.copyDirectory(source, target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return target;
    }

    public static File folderForSampleProject(String moduleName) {
        return new File(separatorsToSystem("target/samples/" + UUID.randomUUID() + "/" + moduleName));
    }

}
