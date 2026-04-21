package online.yanzacademy.fastbuy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;

import online.yanzacademy.fastbuy.entity.Product;
import online.yanzacademy.fastbuy.repository.ProductRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ProductService implements IProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Value("${n8n.webhook.url}")
    private String n8nUrl;

    @Value("${n8n.api.token}")
    private String n8nToken;

    public Map<String, Object> processProductData(MultipartFile file, String nombre, String precio, String cantidad, String detalles) {
        Map<String, Object> response = new HashMap<>();

        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // 1. Configurar los headers para n8n
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(n8nToken); // Usamos Bearer token para la API de n8n

            // 2. Preparar el cuerpo de tipo multipart
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Construimos el JSON string manualmente para evitar dependencias externas como ObjectMapper
            String jsonString = String.format(
                "{\"nombre\":\"%s\",\"precio\":\"%s\",\"cantidad\":\"%s\",\"detalles\":\"%s\"}",
                escapeJson(nombre), escapeJson(precio), escapeJson(cantidad), escapeJson(detalles)
            );

            // Configuramos los headers para esta parte específica ("data")
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> jsonPart = new HttpEntity<>(jsonString, jsonHeaders);
            
            body.add("data", jsonPart);

            // 3. Adjuntar la imagen si existe
            if (file != null && !file.isEmpty()) {
                ByteArrayResource fileAsResource = new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename(); // Necesario para que n8n identifique que es un archivo
                    }
                };
                body.add("file", fileAsResource);
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 4. Enviar a n8n
            System.out.println("Enviando información a n8n... URL: " + n8nUrl);
            ResponseEntity<byte[]> n8nResponse = restTemplate.postForEntity(n8nUrl, requestEntity, byte[].class);
            
            System.out.println("n8n respondió con código: " + n8nResponse.getStatusCode());

            response.put("n8n_status", n8nResponse.getStatusCode().value());
            
            // Aquí capturamos la respuesta enviada por n8n
            byte[] responseBytes = n8nResponse.getBody();
            MediaType n8nContentType = n8nResponse.getHeaders().getContentType();
            
            if (responseBytes != null && responseBytes.length > 0) {
                if (n8nContentType != null && n8nContentType.toString().startsWith("image/")) {
                    String mimeType = n8nContentType.toString();
                    String base64Image = java.util.Base64.getEncoder().encodeToString(responseBytes);
                    String dataUrl = "data:" + mimeType + ";base64," + base64Image;
                    
                    response.put("status", "success");
                    response.put("message", "Información procesada y enviada a n8n exitosamente.");
                    response.put("n8n_response_image", dataUrl);
                } else {
                    // Recibimos respuesta pero no es una imagen (posible mensaje de error de la IA)
                    String aiMessage = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
                    response.put("status", "error");
                    response.put("message", "Mensaje de la IA: " + (aiMessage.isEmpty() ? "No se recibió una imagen válida." : aiMessage));
                }
            } else {
                response.put("status", "error");
                response.put("message", "No se recibió ninguna imagen por parte de la IA.");
            }
            
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // Capturamos los errores HTTP de n8n (ej. 400 Bad Request, 500 Internal Server Error)
            String errorBody = e.getResponseBodyAsString();
            System.err.println("Error HTTP de la IA: " + e.getStatusCode() + " - " + errorBody);
            response.put("status", "error");
            response.put("message", "Mensaje de la IA: " + (errorBody.isEmpty() ? "Error procesando la solicitud." : errorBody));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error comunicándose con n8n: " + e.getMessage());
            response.put("status", "error");
            response.put("message", "Error interno al comunicarse con la IA.");
            response.put("details", e.getMessage());
        }

        return response;
    }

    // Método auxiliar para evitar errores comunes en el JSON
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "");
    }

    @Value("${app.upload.dir:uploads/images}")
    private String uploadDir;

    @Override
    public java.util.List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public Product saveProductFromAI(String base64Image, String nombre, String precio, String cantidad, String detalles, String categoria) throws Exception {
        
        String designImagePath = null;
        
        if (base64Image != null && !base64Image.isEmpty()) {
            Path directoryPath = Paths.get(uploadDir);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }
            
            // Separar posible metadata 'data:image/png;base64,...'
            String[] parts = base64Image.split(",");
            String imageString;
            String extension = ".jpg"; // fallback

            if(parts.length > 1) {
                // Hay metadata
                imageString = parts[1];
                if(parts[0].contains("png")) extension = ".png";
                else if(parts[0].contains("webp")) extension = ".webp";
            } else {
                imageString = base64Image;
            }

            byte[] decodedBytes = java.util.Base64.getDecoder().decode(imageString);

            // Generar un nombre único para evitar colisiones
            String fileName = UUID.randomUUID().toString() + "_" + nombre.replaceAll(" ", "_").toLowerCase() + extension;
            Path filePath = directoryPath.resolve(fileName);
            
            // Descargar el archivo procesado por la IA localmente usando java.nio.file.Files.write
            Files.write(filePath, decodedBytes);
            
            designImagePath = fileName; // Solo guardamos el nombre del archivo para que la BD no maneje rutas absolutas
        }

        Product product = new Product();
        product.setName(nombre);
        product.setPrice(new BigDecimal(precio));
        product.setStock(Integer.parseInt(cantidad));
        product.setDetails(detalles);
        product.setCategory(categoria != null ? categoria : "General");
        product.setDesignImagePath(designImagePath);

        return productRepository.save(product);
    }

    @Override
    public Product updateProduct(Long id, String nombre, String precio, String cantidad, String detalles, String categoria) throws Exception {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new Exception("Producto no encontrado con el id: " + id));
        
        if (nombre != null) product.setName(nombre);
        if (precio != null) product.setPrice(new BigDecimal(precio));
        if (cantidad != null) product.setStock(Integer.parseInt(cantidad));
        if (detalles != null) product.setDetails(detalles);
        if (categoria != null) product.setCategory(categoria);

        return productRepository.save(product);
    }
}
