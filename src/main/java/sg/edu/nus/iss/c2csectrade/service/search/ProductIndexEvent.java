package sg.edu.nus.iss.c2csectrade.service.search;

/** A catalogue change that should be reflected in the search projection. */
public record ProductIndexEvent(Long productId, Change change) {

    public enum Change {
        UPSERT,
        DELETE
    }

    public static ProductIndexEvent upsert(Long productId) {
        return new ProductIndexEvent(productId, Change.UPSERT);
    }

    public static ProductIndexEvent delete(Long productId) {
        return new ProductIndexEvent(productId, Change.DELETE);
    }
}
