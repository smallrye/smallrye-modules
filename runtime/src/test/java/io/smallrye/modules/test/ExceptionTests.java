package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.ModuleLoadException;
import io.smallrye.modules.ModuleNotFoundException;

/**
 * Tests for {@link ModuleLoadException} and {@link ModuleNotFoundException}.
 */
public final class ExceptionTests {

    // --- ModuleLoadException constructors ---

    @Test
    public void loadExceptionNoArg() {
        ModuleLoadException e = new ModuleLoadException();
        assertNull(e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void loadExceptionMessage() {
        ModuleLoadException e = new ModuleLoadException("test message");
        assertEquals("test message", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void loadExceptionCause() {
        RuntimeException cause = new RuntimeException("cause");
        ModuleLoadException e = new ModuleLoadException(cause);
        assertSame(cause, e.getCause());
    }

    @Test
    public void loadExceptionMessageAndCause() {
        RuntimeException cause = new RuntimeException("cause");
        ModuleLoadException e = new ModuleLoadException("msg", cause);
        assertEquals("msg", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test
    public void loadExceptionIsRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(ModuleLoadException.class));
    }

    // --- ModuleNotFoundException constructors ---

    @Test
    public void notFoundExceptionNoArg() {
        ModuleNotFoundException e = new ModuleNotFoundException();
        assertNull(e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void notFoundExceptionMessage() {
        ModuleNotFoundException e = new ModuleNotFoundException("not found");
        assertEquals("not found", e.getMessage());
    }

    @Test
    public void notFoundExceptionCause() {
        RuntimeException cause = new RuntimeException();
        ModuleNotFoundException e = new ModuleNotFoundException(cause);
        assertSame(cause, e.getCause());
    }

    @Test
    public void notFoundExceptionMessageAndCause() {
        RuntimeException cause = new RuntimeException();
        ModuleNotFoundException e = new ModuleNotFoundException("msg", cause);
        assertEquals("msg", e.getMessage());
        assertSame(cause, e.getCause());
    }

    @Test
    public void notFoundExceptionExtendsLoadException() {
        assertTrue(ModuleLoadException.class.isAssignableFrom(ModuleNotFoundException.class));
    }
}
