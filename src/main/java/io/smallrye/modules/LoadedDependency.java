package io.smallrye.modules;

import io.smallrye.common.constraint.Assert;
import io.smallrye.modules.desc.Dependency;

/**
 * A loaded dependency.
 */
record LoadedDependency(Dependency dependency, LoadedModule loadedModule) {
    LoadedDependency {
        Assert.checkNotNullParam("dependency", dependency);
        Assert.checkNotNullParam("loadedModule", loadedModule);
    }
}
