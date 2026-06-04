package network.tserver.tnexus.util;

/**
 * Supplies a value and allows checked exceptions.
 *
 * @param <T> supplied type
 * @param <E> exception type
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {

    /**
     * Gets a value.
     *
     * @return supplied value
     * @throws E when the value cannot be produced
     */
    T get() throws E;
}
