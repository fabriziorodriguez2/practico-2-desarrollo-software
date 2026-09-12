package com.cinebuscador.controller;

import com.cinebuscador.model.Pelicula;
import com.cinebuscador.repository.PeliculaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
public class PeliculaController {

    private final PeliculaRepository peliculaRepo;

    @Value("${app.upload-dir}")
    private String uploadDir;

    // Whitelist de extensiones y tipos MIME permitidos (solo imagenes de afiche)
    private static final Set<String> EXT_PERMITIDAS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> MIME_PERMITIDOS = Set.of("image/jpeg", "image/png");
    private static final long MAX_SIZE = 2L * 1024 * 1024; // 2 MB

    public PeliculaController(PeliculaRepository peliculaRepo) {
        this.peliculaRepo = peliculaRepo;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String buscar,
                        @RequestParam(required = false, defaultValue = "nombre") String ordenarPor,
                        @RequestParam(required = false, defaultValue = "ASC") String sentido,
                        Model model) {

        if (buscar != null && !buscar.isBlank()) {
            List<Object[]> resultadosRaw;
            if ("DESC".equalsIgnoreCase(sentido)) {
                resultadosRaw = peliculaRepo.searchWithFuncionesDesc(buscar, ordenarPor);
            } else {
                resultadosRaw = peliculaRepo.searchWithFunciones(buscar, ordenarPor);
            }

            // Wrap Object[] in Maps for cleaner Thymeleaf access
            List<Map<String, Object>> resultados = new java.util.ArrayList<>();
            for (Object[] row : resultadosRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id",        row[0]);
                map.put("nombre",    row[1]);
                map.put("fechaHora", row[2]);
                map.put("disponibles", row[3]);
                map.put("descripcion", row[4]);
                map.put("afichePath", row[5]);
                resultados.add(map);
            }
            model.addAttribute("resultados", resultados);
        }

        model.addAttribute("query", buscar != null ? buscar : "");
        model.addAttribute("sort_by", ordenarPor);
        model.addAttribute("sort_dir", sentido);
        return "index";
    }

    @GetMapping("/upload/{id}")
    public String uploadForm(@PathVariable Integer id, Model model) {
        Pelicula pelicula = peliculaRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pelicula no encontrada"));
        model.addAttribute("pelicula", pelicula);
        return "upload";
    }

    @PostMapping("/upload/{id}")
    public String uploadFile(@PathVariable Integer id,
                             @RequestParam("afiche") MultipartFile archivo,
                             RedirectAttributes ra) throws IOException {
        Pelicula pelicula = peliculaRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pelicula no encontrada"));

        // 1. El archivo no puede estar vacio
        if (archivo.isEmpty()) {
            ra.addFlashAttribute("error", "El archivo esta vacio");
            return "redirect:/upload/" + id;
        }

        // 2. Limite de tamanio
        if (archivo.getSize() > MAX_SIZE) {
            ra.addFlashAttribute("error", "El archivo supera el tamanio maximo permitido (2 MB)");
            return "redirect:/upload/" + id;
        }

        // 3. Validar la extension declarada contra la whitelist
        String original = StringUtils.cleanPath(
                archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename());
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot + 1).toLowerCase();
        }
        if (!EXT_PERMITIDAS.contains(ext)) {
            ra.addFlashAttribute("error", "Tipo de archivo no permitido. Solo se aceptan JPG y PNG.");
            return "redirect:/upload/" + id;
        }

        // 4. Validar el CONTENIDO real del archivo (magic bytes), no confiar en la extension
        String contentType = archivo.getContentType();
        String detectado;
        try (BufferedInputStream bis = new BufferedInputStream(archivo.getInputStream())) {
            detectado = URLConnection.guessContentTypeFromStream(bis);
        }
        if (contentType == null || !MIME_PERMITIDOS.contains(contentType)
                || detectado == null || !MIME_PERMITIDOS.contains(detectado)) {
            ra.addFlashAttribute("error", "El contenido del archivo no corresponde a una imagen valida.");
            return "redirect:/upload/" + id;
        }

        // 5. Generar un nombre aleatorio: NO se usa el nombre enviado por el usuario
        String safeName = UUID.randomUUID() + "." + ext;

        // 6. Resolver dentro del directorio y verificar que no se escape (path traversal)
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        Path destino = uploadPath.resolve(safeName).normalize();
        if (!destino.startsWith(uploadPath)) {
            ra.addFlashAttribute("error", "Ruta de destino invalida");
            return "redirect:/upload/" + id;
        }

        Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

        pelicula.setAfichePath(safeName);
        peliculaRepo.save(pelicula);

        return "redirect:/";
    }

    @GetMapping("/uploads/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws IOException {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = base.resolve(filename).normalize();
        // Defensa contra path traversal al servir el archivo
        if (!filePath.startsWith(base)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(filePath.toUri());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
            .body(resource);
    }

}