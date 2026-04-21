package online.yanzacademy.fastbuy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*") // Permite peticiones desde tu frontend (ej. localhost:4200) sin importar el puerto
public class TestFileController {

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            System.out.println("No se recibió ningún archivo.");
            return ResponseEntity.badRequest().body("El archivo está vacío");
        }
        
        // Muestra en la consola de Spring Boot que fue exitoso
        System.out.println("¡Success! Archivo recibido correctamente.");
        System.out.println("Nombre del archivo: " + file.getOriginalFilename());
        System.out.println("Tamaño: " + file.getSize() + " bytes");
        System.out.println("Tipo de contenido: " + file.getContentType());
        
        return ResponseEntity.ok("Success");
    }
}
