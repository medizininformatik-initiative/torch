package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;

/**
 * Builds per-thread {@link IFhirPath} engines for classes evaluated concurrently (e.g. under
 * {@code parallelStream()}), since {@code FHIRPathEngine} is not safe for concurrent use.
 */
public final class FhirPathEngines {

    private FhirPathEngines() {
    }

    /**
     * Returns a {@link ThreadLocal} that lazily builds one {@link IFhirPath} engine per thread.
     *
     * <p>Each engine is built using the context classloader captured when this method is called,
     * not the classloader of whichever thread first calls {@link ThreadLocal#get()}. This matters
     * because {@code ForkJoinPool.commonPool()} worker threads (used implicitly by
     * {@code parallelStream()}) are not guaranteed to carry the application classloader, which
     * breaks HAPI's {@code ServiceLoader}-based cache provider lookup inside a repackaged Spring
     * Boot jar.
     *
     * @param ctx the FhirContext to use for creating engines
     */
    public static ThreadLocal<IFhirPath> threadLocal(FhirContext ctx) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return ThreadLocal.withInitial(() -> {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(classLoader);
            try {
                return ctx.newFhirPath();
            } finally {
                thread.setContextClassLoader(previous);
            }
        });
    }
}
