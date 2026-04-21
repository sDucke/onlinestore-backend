package online.yanzacademy.fastbuy.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class AIVisionService {

    @Value("${openai.api.key}")
    private String apiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    public String validateProductImage(MultipartFile file) {
        try {
            // 1. Convertir imagen a Base64
            byte[] bytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(bytes);
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String dataUrl = "data:" + mimeType + ";base64," + base64Image;

            // 2. Preparar los Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 3. Preparar el Cuerpo de la petición (JSON)
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o"); // Usamos el modelo optimizado que soporta visión

            Map<String, Object> textMessage = new HashMap<>();
            textMessage.put("type", "text");
            textMessage.put("text", "Analiza la imagen. Solo responde con una única palabra en minúscula: 'valido' si la imagen muestra clara y prominentemente un producto tecnológico en venta (celulares, computadoras, accesorios, componentes, etc). Responde 'invalido' en cualquier otro caso (personas, memes, paisajes, animales, cosas irrelevantes).");

            Map<String, Object> imageMessage = new HashMap<>();
            imageMessage.put("type", "image_url");
            Map<String, String> imageUrl = new HashMap<>();
            imageUrl.put("url", dataUrl);
            imageMessage.put("image_url", imageUrl);

            Map<String, Object> messageContent = new HashMap<>();
            messageContent.put("role", "user");
            messageContent.put("content", Arrays.asList(textMessage, imageMessage));

            body.put("messages", Collections.singletonList(messageContent));
            body.put("max_tokens", 10);
            body.put("temperature", 0.0); // Cero creatividad para una respuesta estricta

            // 4. Enviar la petición HTTP a OpenAI
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_URL, requestEntity, Map.class);
            
            // 5. Analizar la respuesta
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    
                    if (content != null) {
                        String cleanContent = content.trim().toLowerCase();
                        if (cleanContent.contains("invalido")) {
                            return "invalido";
                        } else if (cleanContent.contains("valido")) {
                            return "valido";
                        }
                    }
                }
            }
            return "error_al_subir";

        } catch (Exception e) {
            System.err.println("Error procesando imagen con OpenAI: " + e.getMessage());
            return "error_al_subir";
        }
    }
}
