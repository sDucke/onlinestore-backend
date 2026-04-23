package online.yanzacademy.fastbuy.controller;

import online.yanzacademy.fastbuy.service.IProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*") // Permite acceso desde el frontend
public class ProductController {

    private final IProductService productService;

    public ProductController(IProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadProduct(
            @RequestParam("file") MultipartFile file,
            @RequestParam("nombre") String nombre,
            @RequestParam("precio") String precio,
            @RequestParam("cantidad") String cantidad,
            @RequestParam("detalles") String detalles) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "La imagen es obligatoria."));
        }

        // 1. Delegamos a n8n para que genere el diseño mágico
        Map<String, Object> response = productService.processProductData(file, nombre, precio, cantidad, detalles);

        // 2. Si fue exitoso, la IA nos mandó la imagen de vuelta, extraigámosla y guardemos en BD:
        if ("success".equals(response.get("status")) && response.containsKey("n8n_response_image")) {
            String base64GeneratedImage = (String) response.get("n8n_response_image");
            String socialPost = response.containsKey("social_post") ? (String) response.get("social_post") : "";
            
            try {
                // Guardar localmente y en PostgreSQL usando el Base64 retornado por n8n
                productService.saveProductFromAI(base64GeneratedImage, nombre, precio, cantidad, detalles, "General", socialPost);
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Error al guardar diseño de n8n en BD: " + e.getMessage()));
            }
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<java.util.List<online.yanzacademy.fastbuy.entity.Product>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable("id") Long id,
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "precio", required = false) String precio,
            @RequestParam(value = "cantidad", required = false) String cantidad,
            @RequestParam(value = "detalles", required = false) String detalles,
            @RequestParam(value = "categoria", required = false) String categoria) {
        
        try {
            online.yanzacademy.fastbuy.entity.Product updated = productService.updateProduct(id, nombre, precio, cantidad, detalles, categoria);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Producto actualizado", "product", updated));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
