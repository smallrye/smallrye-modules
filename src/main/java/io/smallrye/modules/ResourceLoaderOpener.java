package io.smallrye.modules;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import io.smallrye.common.resource.JarFileResourceLoader;
import io.smallrye.common.resource.PathResourceLoader;
import io.smallrye.common.resource.Resource;
import io.smallrye.common.resource.ResourceLoader;
import io.smallrye.common.resource.URLResourceLoader;

/**
 * An opener for a resource loader.
 * Resource loaders are opened when a module is defined and closed when a module is unloaded.
 * The openers may be executed under a lock, so they should not block for long periods of time or depend on other locks.
 */
public interface ResourceLoaderOpener {
    /**
     * Open the resource loader.
     *
     * @return the resource loader (must not be {@code null})
     * @throws IOException if the resource loader cannot be opened due to an I/O error
     * @throws RuntimeException if the resource loader cannot be opened for some other reason
     */
    ResourceLoader open() throws IOException, RuntimeException;

    /**
     * An opener that just returns the given loader always.
     *
     * @param loader the loader (must not be {@code null})
     * @return the opener (not {@code null})
     */
    static ResourceLoaderOpener forLoader(ResourceLoader loader) {
        return () -> loader;
    }

    /**
     * {@return an opener that creates a path-based resource loader for the given directory}
     *
     * @param path the directory path (must not be {@code null})
     */
    static ResourceLoaderOpener forDirectory(Path path) {
        return () -> new PathResourceLoader(path);
    }

    /**
     * {@return an opener that creates a JAR file resource loader for the given path}
     *
     * @param jarPath the JAR file path (must not be {@code null})
     */
    static ResourceLoaderOpener forJarFile(Path jarPath) {
        return () -> new JarFileResourceLoader(jarPath);
    }

    /**
     * {@return an opener that creates a JAR file resource loader for the given resource}
     *
     * @param jarResource the JAR file resource (must not be {@code null})
     */
    static ResourceLoaderOpener forJarResource(Resource jarResource) {
        return () -> new JarFileResourceLoader(jarResource);
    }

    /**
     * {@return an opener that creates a URL-based resource loader for the given URL}
     *
     * @param url the URL (must not be {@code null})
     */
    static ResourceLoaderOpener forURL(URL url) {
        return () -> new URLResourceLoader(url);
    }
}
