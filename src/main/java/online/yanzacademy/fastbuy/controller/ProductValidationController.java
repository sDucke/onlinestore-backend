package online.yanzacademy.fastbuy.controller;

import online.yanzacademy.fastbuy.dto.ValidationResponse;
import online.yanzacademy.fastbuy.services.AIVisionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*") // Permite acceso desde tu frontend Angular
public class ProductValidationController {

    private final AIVisionService aiVisionService;

    public ProductValidationController(AIVisionService aiVisionService) {
        this.aiVisionService = aiVisionService;
    }

    @PostMapping("/validate-image")
    public ResponseEntity<ValidationResponse> validateImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            // Si no subieron nada, devolvemos el error estructurado localmente
            return ResponseEntity.badRequest().body(new ValidationResponse("error_al_subir"));
        }

        // 1. Llamar al servicio que hace la magia con OpenAI usando la imagen
        String iaResult = aiVisionService.validateProductImage(file);
        
        // 2. Empaquetar el resultado en formato JSON mediante nuestro DTO
        ValidationResponse jsonResponse = new ValidationResponse(iaResult);

        // Si falló por OpenAI podríamos devolver 500, pero vamos a devolver OK(200) para 
        // que el frontend parsee fácilmente el 'error_al_subir' dentro de su propia lógica de negocio.
        return ResponseEntity.ok(jsonResponse); 
    }
}
