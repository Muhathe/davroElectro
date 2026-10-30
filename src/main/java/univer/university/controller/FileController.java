package univer.university.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import univer.university.service.ProductImageService;

/** Tovar rasmlari tokensiz ochiq (img src header yubora olmaydi). URL kontent xeshi bilan — abadiy keshlanadi. */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final ProductImageService images;

    @GetMapping("/files/products/{productId}/{fileName:.+}")
    public ResponseEntity<Resource> productImage(@PathVariable Long productId, @PathVariable String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(images.resolve(productId, fileName)));
    }
}
