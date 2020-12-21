package scaffolding;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;

import java.io.File;
import java.nio.file.Files;

import static scaffolding.Photocopier.copySampleAppToTempDir;

public class AppRepo {

    public static AppRepo create(String name) {
        try {
            File originDir = copySampleAppToTempDir(name);

            InitCommand initCommand = Git.init();
            initCommand.setDirectory(originDir);
            Git origin = initCommand.call();
            String defaultBranch = origin.getRepository().getBranch();

            origin.add().addFilepattern(".").call();
            origin.commit().setMessage("Initial commit").call();
            origin.branchCreate().setName("branch-1").call();

            origin.branchCreate().setName("branch-2").call();
            origin.branchCreate().setName("branch-3").call();
            origin.branchCreate().setName("branch-4").call();

            // Create new new branch that is ahead of the default but has no build scripts so a build would fail
            origin.branchCreate().setName("ahead").call();
            Files.deleteIfExists(new File(originDir, "build.sh").toPath());
            Files.deleteIfExists(new File(originDir, "build.bat").toPath());
            origin.commit().setMessage("Second commit").call();

            origin.checkout().setName(defaultBranch).call();

            return new AppRepo(name, originDir, origin);
        } catch (Exception e) {
            throw new RuntimeException("Error while creating git repo", e);
        }
    }

    public final String name;
    public final File originDir;
    public final Git origin;

    private AppRepo(String name, File originDir, Git origin) {
        this.name = name;
        this.originDir = originDir;
        this.origin = origin;
    }

    public String gitUrl() {
        return originDir.toURI().toString();
    }

    public void close() {
        origin.close();
    }
}
