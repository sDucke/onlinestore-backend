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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import online.yanzacademy.fastbuy.entity.Product;
import online.yanzacademy.fastbuy.repository.ProductRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;
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
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(180000); // 3 minutos
            requestFactory.setReadTimeout(180000);    // 3 minutos
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            // 1. Configurar los headers para n8n
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(n8nToken); // Usamos Bearer token para la API de n8n

            // 2. Preparar el cuerpo de tipo multipart
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Construimos el JSON string manualmente para evitar dependencias externas como ObjectMapper
            String detallesObj = (detalles != null && detalles.trim().startsWith("{")) ? detalles : "\"" + escapeJson(detalles) + "\"";
            String jsonString = String.format(
                "{\"nombre\":\"%s\",\"precio\":\"%s\",\"cantidad\":\"%s\",\"detalles\":%s}",
                escapeJson(nombre), escapeJson(precio), escapeJson(cantidad), detallesObj
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
                String responseStr = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
                if (n8nContentType != null && n8nContentType.toString().startsWith("application/json")) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(responseStr);
                    
                    if (rootNode.has("image_base64")) {
                        String mimeType = rootNode.has("mimeType") ? rootNode.get("mimeType").asText() : "image/png";
                        String base64Image = rootNode.get("image_base64").asText();
                        String dataUrl = "data:" + mimeType + ";base64," + base64Image;
                        
                        response.put("status", "success");
                        response.put("message", "Información procesada y enviada a n8n exitosamente.");
                        response.put("n8n_response_image", dataUrl);
                        
                        if (rootNode.has("social_post")) {
                            JsonNode socialPostNode = rootNode.get("social_post");
                            String hook = socialPostNode.has("hook") ? socialPostNode.get("hook").asText() : "";
                            String texto = socialPostNode.has("texto_publicacion") ? socialPostNode.get("texto_publicacion").asText() : "";
                            String cta = socialPostNode.has("cta") ? socialPostNode.get("cta").asText() : "";
                            
                            // Asegurarse de que el texto incluya el precio
                            if (!texto.contains(precio)) {
                                texto += " | Precio: $" + precio;
                            }
                            
                            StringBuilder hashtagsBuilder = new StringBuilder();
                            if (socialPostNode.has("hashtags")) {
                                for (JsonNode tag : socialPostNode.get("hashtags")) {
                                    hashtagsBuilder.append(tag.asText()).append(" ");
                                }
                            }
                            
                            String finalSocialPost = hook + "\n\n" + texto + "\n\n" + cta + "\n\n" + hashtagsBuilder.toString().trim();
                            response.put("social_post", finalSocialPost);
                        }
                    } else {
                        response.put("status", "error");
                        response.put("message", "La IA devolvió JSON pero no se encontró la imagen base64.");
                    }
                } else if (n8nContentType != null && n8nContentType.toString().startsWith("image/")) {
                    // Fallback in case n8n still returns a raw image
                    String mimeType = n8nContentType.toString();
                    String base64Image = java.util.Base64.getEncoder().encodeToString(responseBytes);
                    String dataUrl = "data:" + mimeType + ";base64," + base64Image;
                    
                    response.put("status", "success");
                    response.put("message", "Información procesada y enviada a n8n exitosamente.");
                    response.put("n8n_response_image", dataUrl);
                } else {
                    response.put("status", "error");
                    response.put("message", "Mensaje de la IA: " + (responseStr.isEmpty() ? "Respuesta desconocida." : responseStr));
                }
            } else {
                response.put("status", "error");
                response.put("message", "No se recibió ninguna respuesta por parte de la IA.");
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

    @Value("${app.upload.dir:/root/images}")
    private String uploadDir;

    @Override
    public List<Product> getAllProducts() {
        List<Product> products = productRepository.findAll();

        boolean hasPathFixes = false;
        for (Product product : products) {
            String currentPath = product.getDesignImagePath();
            String normalizedPath = normalizeStoredImagePath(currentPath);
            if (currentPath != null && normalizedPath == null) {
                product.setDesignImagePath(null);
                hasPathFixes = true;
                continue;
            }
            if (normalizedPath != null && !normalizedPath.equals(currentPath)) {
                product.setDesignImagePath(normalizedPath);
                hasPathFixes = true;
            }
        }

        if (hasPathFixes) {
            for (Product product : products) {
                productRepository.save(product);
            }
        }

        return products;
    }

    @Override
    public Product saveProductFromAI(String base64Image, String nombre, String precio, String cantidad, String detalles, String categoria, String socialPost) throws Exception {
        
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
            String safeName = sanitizeFileName(nombre);
            String fileName = UUID.randomUUID() + "_" + safeName + extension;
            Path filePath = directoryPath.resolve(fileName);
            
            // Descargar el archivo procesado por la IA localmente usando java.nio.file.Files.write
            Files.write(filePath, decodedBytes);
            
            designImagePath = normalizeStoredImagePath(fileName);
        }

        String finalDetails = detalles;
        try {
            if (detalles != null && detalles.trim().startsWith("{")) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(detalles);
                if (node.has("texto")) {
                    finalDetails = node.get("texto").asText();
                }
            }
        } catch (Exception e) {
            System.err.println("Error procesando detalles JSON al guardar en BD: " + e.getMessage());
        }

        Product product = new Product();
        product.setName(nombre);
        product.setPrice(new BigDecimal(precio));
        product.setStock(Integer.parseInt(cantidad));
        product.setDetails(finalDetails);
        product.setCategory(categoria != null ? categoria : "General");
        product.setDesignImagePath(designImagePath);
        product.setSocialPost(socialPost);

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

    @Override
    public void deleteProduct(Long id) throws Exception {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new Exception("Producto no encontrado con el id: " + id));
        
        // Opcional: Eliminar la imagen asociada si existe localmente
        String normalizedPath = normalizeStoredImagePath(product.getDesignImagePath());
        if (normalizedPath != null) {
            Path imagePath = Paths.get(uploadDir).resolve(normalizedPath);
            try {
                Files.deleteIfExists(imagePath);
            } catch (IOException e) {
                System.err.println("No se pudo eliminar la imagen: " + e.getMessage());
            }
        }

        productRepository.delete(product);
    }

    private String sanitizeFileName(String input) {
        String safeInput = input == null ? "" : input.trim();
        if (safeInput.isEmpty()) {
            return "producto";
        }

        String ascii = Normalizer.normalize(safeInput, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String sanitized = ascii.toLowerCase()
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? "producto" : sanitized;
    }

    private String normalizeStoredImagePath(String rawPath) {
        if (rawPath == null) {
            return null;
        }

        String normalized = rawPath.trim().replace("\\", "/");
        if (normalized.isEmpty()) {
            return null;
        }

        int querySeparator = normalized.indexOf('?');
        if (querySeparator >= 0) {
            normalized = normalized.substring(0, querySeparator);
        }

        String apiPrefix = "/api/images/";
        int apiPrefixPos = normalized.indexOf(apiPrefix);
        if (apiPrefixPos >= 0) {
            normalized = normalized.substring(apiPrefixPos + apiPrefix.length());
        }

        if (normalized.startsWith("file:")) {
            try {
                Path uriPath = Paths.get(java.net.URI.create(normalized));
                normalized = uriPath.getFileName() != null ? uriPath.getFileName().toString() : "";
            } catch (Exception ignored) {
                int lastSlash = normalized.lastIndexOf('/');
                normalized = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
            }
        }

        if (normalized.contains("/")) {
            normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        }

        return normalized.isEmpty() ? null : normalized;
    }
}
