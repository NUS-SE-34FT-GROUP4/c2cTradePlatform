package sg.edu.nus.iss.c2csectrade.service.search;

import sg.edu.nus.iss.c2csectrade.entity.Product;

import java.util.Collection;

/** Boundary between catalogue writes and the replaceable search engine. */
public interface ProductSearchIndex {

    void rebuild(Collection<Product> products);

    void upsert(Product product);

    void delete(Long productId);
}
