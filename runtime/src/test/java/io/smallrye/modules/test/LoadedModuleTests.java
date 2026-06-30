package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.LoadedModule;

/**
 * Tests for {@link LoadedModule}.
 */
public final class LoadedModuleTests {

    // --- forModule with boot module ---

    @Test
    public void forModuleBootModule() {
        Module javaBase = Object.class.getModule();
        LoadedModule lm = LoadedModule.forModule(javaBase);
        assertNotNull(lm);
        assertSame(javaBase, lm.module());
    }

    @Test
    public void namePresent() {
        LoadedModule lm = LoadedModule.forModule(Object.class.getModule());
        assertEquals(Optional.of("java.base"), lm.name());
    }

    @Test
    public void classLoaderIsBootForJavaBase() {
        LoadedModule lm = LoadedModule.forModule(Object.class.getModule());
        // java.base has null class loader (bootstrap)
        assertNull(lm.classLoader());
    }

    @Test
    public void forModuleNonBootModule() {
        // java.xml is transitively required by our module
        Module javaXml = javax.xml.stream.XMLInputFactory.class.getModule();
        LoadedModule lm = LoadedModule.forModule(javaXml);
        assertNotNull(lm);
    }

    // --- equals ---

    @Test
    public void equalsSameModule() {
        Module javaBase = Object.class.getModule();
        LoadedModule a = LoadedModule.forModule(javaBase);
        LoadedModule b = LoadedModule.forModule(javaBase);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsDifferentModules() {
        LoadedModule a = LoadedModule.forModule(Object.class.getModule());
        LoadedModule b = LoadedModule.forModule(javax.xml.stream.XMLInputFactory.class.getModule());
        assertNotEquals(a, b);
    }

    @Test
    public void equalsNullSafe() {
        LoadedModule lm = LoadedModule.forModule(Object.class.getModule());
        assertNotEquals(null, lm);
        assertFalse(lm.equals((LoadedModule) null));
    }

    @Test
    public void equalsWrongType() {
        LoadedModule lm = LoadedModule.forModule(Object.class.getModule());
        assertNotEquals("not a loaded module", lm);
    }

    // --- toString ---

    @Test
    public void toStringBootModule() {
        LoadedModule lm = LoadedModule.forModule(Object.class.getModule());
        String s = lm.toString();
        assertNotNull(s);
        assertTrue(s.contains("java.base"), s);
    }
}
