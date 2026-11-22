package com.gkcontas.nativeimage.product;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    List<Product> findByCategoryOrderByIdAsc(String category, Limit limit);
}
