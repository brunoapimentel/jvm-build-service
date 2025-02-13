package com.redhat.hacbs.recipies.location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Manages an individual recipe database of build recipes.
 * <p>
 * Layout is specified as:
 * <p>
 * /recipes/io/quarkus/ //location information for everything in the io/quarkus group
 * /recipes/io/quarkus/security/ //info for the io.quarkus.security group
 * /recipes/io/quarkus/_artifact/quarkus-core/ //artifact level information for quarkus-core (hopefully not common)
 * /recipes/io/quarkus/_version/2.2.0-rhosk3/ //location information for version 2.2.0-rhosk3
 * /recipes/io/quarkus/_artifact/quarkus-core/_version/2.2.0-rhosk3/ //artifact level information for a specific version of
 * quarkus core
 * <p>
 * Different pieces of information are stored in different files in these directories specified above, and it is possible
 * to only override some parts of the recipe (e.g. a different location for a service specific version, but everything else is
 * the same)
 * <p>
 * At present this is just the location information.
 */
public class RecipeLayoutManager implements RecipeDirectory {

    private static final Logger log = Logger.getLogger(RecipeLayoutManager.class.getName());

    public static final String ARTIFACT = "_artifact";
    public static final String VERSION = "_version";

    private final Path checkoutDirectory;
    private final Path scmInfoDirectory;
    private final Path buildInfoDirectory;

    public RecipeLayoutManager(Path baseDirectory) {
        this.checkoutDirectory = baseDirectory;
        this.scmInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.SCM_INFO);
        this.buildInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.BUILD_INFO);
    }

    /**
     * Returns the directories that contain the recipe information for this specific artifact
     */
    public Optional<RecipePathMatch> getArtifactPaths(String groupId, String artifactId, String version) {
        Path groupPath = this.scmInfoDirectory.resolve(groupId.replace('.', File.separatorChar));
        Path artifactFolder = groupPath.resolve(ARTIFACT);
        Path artifactPath = artifactFolder.resolve(artifactId);
        Path artifactAndVersionPath = null;
        if (!Files.exists(groupPath)) {
            return Optional.empty();
        }
        boolean groupAuthoritative = true;
        if (Files.exists(artifactPath)) {
            artifactAndVersionPath = resolveVersion(artifactPath, version).orElse(null);
            groupAuthoritative = false;
        } else {
            artifactPath = null;
        }
        Path versionPath = resolveVersion(groupPath, version).orElse(null);
        if (versionPath != null) {
            groupAuthoritative = false;
        }

        return Optional
                .of(new RecipePathMatch(groupPath, artifactPath, versionPath, artifactAndVersionPath, groupAuthoritative));
    }

    @Override
    public Optional<Path> getBuildPaths(String scmUri, String version) {
        Path target = buildInfoDirectory.resolve(scmUri);
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        return Optional.of(resolveVersion(target, version).orElse(target));
    }

    private Optional<Path> resolveVersion(Path target, String version) {
        Path versions = target.resolve(VERSION);
        if (!Files.exists(versions)) {
            return Optional.empty();
        }
        ComparableVersion requestedVersion = new ComparableVersion(version);
        ComparableVersion currentVersion = null;
        Path currentPath = null;
        try (var s = Files.list(versions)) {
            for (var path : s.toList()) {
                ComparableVersion pv = new ComparableVersion(path.getFileName().toString());
                if (requestedVersion.compareTo(pv) <= 0) {
                    if (currentVersion == null || pv.compareTo(currentVersion) > 0) {
                        currentVersion = pv;
                        currentPath = path;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Optional.ofNullable(currentPath);
    }

    @Override
    public <T> void writeArtifactData(AddRecipeRequest<T> data) {
        String groupId = data.getGroupId();
        String artifactId = data.getArtifactId();
        String version = data.getVersion();
        Path resolved = this.scmInfoDirectory.resolve(groupId.replace('.', File.separatorChar));
        if (artifactId != null) {
            resolved = resolved.resolve(ARTIFACT);
            resolved = resolved.resolve(artifactId);
        }
        if (version != null) {
            resolved = resolved.resolve(VERSION);
            resolved = resolved.resolve(version);
        }
        try {
            Files.createDirectories(resolved);
            data.getRecipe().getHandler().write(data.getData(), resolved.resolve(data.getRecipe().getName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
