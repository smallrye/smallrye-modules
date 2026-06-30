package io.smallrye.modules.desc;

/**
 * A modifier for a module descriptor item.
 */
public sealed interface ModifierFlag permits Dependency.Modifier, ModuleDescriptor.Modifier {
}
