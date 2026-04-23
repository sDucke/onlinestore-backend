package online.yanzacademy.fastbuy.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import online.yanzacademy.fastbuy.entity.Product;

@Repository
public class ProductRepository {

    @Autowired
    private IProductRepository repository;

    public Product save(Product product) {
        return repository.save(product);
    }

    public java.util.List<Product> findAll() {
        return repository.findAll();
    }

    public java.util.Optional<Product> findById(Long id) {
        return repository.findById(id);
    }

    public void delete(Product product) {
        repository.delete(product);
    }
}
