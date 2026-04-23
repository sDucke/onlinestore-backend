package online.yanzacademy.fastbuy.service;

import online.yanzacademy.fastbuy.entity.Product;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface IProductService {
    
    // Método actual para interactuar con n8n
    Map<String, Object> processProductData(MultipartFile file, String nombre, String precio, String cantidad, String detalles);

    // Nuevo método para listar datos completos de PostgreSQL
    java.util.List<Product> getAllProducts();

    // Guardará localmente y en BD después de recibir la imagen procesada en Base64 por la IA
    Product saveProductFromAI(String base64Image, String nombre, String precio, String cantidad, String detalles, String categoria, String socialPost) throws Exception;

    // Actualizar producto
    Product updateProduct(Long id, String nombre, String precio, String cantidad, String detalles, String categoria) throws Exception;

    // Eliminar producto
    void deleteProduct(Long id) throws Exception;
}
