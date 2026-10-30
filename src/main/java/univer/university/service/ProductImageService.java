package univer.university.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import univer.university.dto.response.ProductResponses.ProductDto;
import univer.university.entity.Product;
import univer.university.exception.BusinessException;
import univer.university.exception.NotFoundException;
import univer.university.repository.ProductRepository;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tovar rasmi: signatura bo'yicha tekshiradi (JPEG/PNG/WEBP), 800 px va 200 px JPEG qilib saqlaydi.
 * Qayta kodlash EXIF va boshqa metama'lumotlarni olib tashlaydi. Fayl nomi — kontent xeshi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAIN_SIZE = 800;
    private static final int THUMB_SIZE = 200;
    /** Dekompressiya bombasidan himoya. */
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static final Pattern FILE_NAME = Pattern.compile("^[a-f0-9]{16}(_thumb)?\\.jpg$");

    private final ProductRepository products;
    private final AuditService audit;

    @Value("${app.files.dir}")
    private String filesDir;

    public static String imageUrl(Product p) {
        return p.getImageKey() == null ? null : "/files/products/" + p.getId() + "/" + p.getImageKey() + ".jpg";
    }

    public static String thumbUrl(Product p) {
        return p.getImageKey() == null ? null : "/files/products/" + p.getId() + "/" + p.getImageKey() + "_thumb.jpg";
    }

    @Transactional
    public ProductDto upload(Long productId, MultipartFile file) {
        Product p = find(productId);
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Rasm fayli tanlanmagan");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("Rasm 5 MB dan katta", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        byte[] src;
        try {
            src = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.badRequest("Rasm fayli buzilgan");
        }
        if (!isSupported(src)) {
            throw BusinessException.badRequest("Faqat JPG, PNG yoki WEBP rasm");
        }
        BufferedImage img = decode(src);
        byte[] main = jpeg(fit(img, MAIN_SIZE), 0.85f);
        byte[] thumb = jpeg(fit(img, THUMB_SIZE), 0.80f);
        String key = sha256(main).substring(0, 16);

        Path dir = productDir(productId);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(key + ".jpg"), main);
            Files.write(dir.resolve(key + "_thumb.jpg"), thumb);
        } catch (IOException e) {
            log.error("Rasmni saqlab bo'lmadi: {}", dir, e);
            throw new BusinessException("Rasmni saqlab bo'lmadi", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        p.setImageKey(key);
        audit.log("IMAGE", "Product", p.getId(), "rasm=" + key);
        afterCommit(() -> cleanup(dir, key));
        return ProductService.toDto(p);
    }

    @Transactional
    public void delete(Long productId) {
        Product p = find(productId);
        if (p.getImageKey() == null) {
            return;
        }
        p.setImageKey(null);
        audit.log("IMAGE_DELETE", "Product", p.getId(), null);
        Path dir = productDir(productId);
        afterCommit(() -> cleanup(dir, null));
    }

    /** Ochiq berish uchun faylni topadi; nom qat'iy shablonga mos bo'lishi shart (path traversal yo'q). */
    public Path resolve(Long productId, String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new NotFoundException("Fayl topilmadi");
        }
        Path f = productDir(productId).resolve(fileName);
        if (!Files.isRegularFile(f)) {
            throw new NotFoundException("Fayl topilmadi");
        }
        return f;
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Tovar topilmadi"));
    }

    private Path productDir(Long productId) {
        return Paths.get(filesDir, "products", String.valueOf(productId)).toAbsolutePath().normalize();
    }

    static boolean isSupported(byte[] b) {
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
        boolean webp = b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || png || webp;
    }

    private static BufferedImage decode(byte[] src) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(src))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw BusinessException.badRequest("Rasm fayli buzilgan");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_SOURCE_PIXELS) {
                    throw BusinessException.badRequest("Rasm o'lchami juda katta");
                }
                BufferedImage img = reader.read(0);
                if (img == null) {
                    throw BusinessException.badRequest("Rasm fayli buzilgan");
                }
                return img;
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw BusinessException.badRequest("Rasm fayli buzilgan");
        }
    }

    /** Uzun tomoni max px dan oshmaydigan RGB rasm (kattalashtirmaydi, shaffof joy oq bo'ladi). */
    private static BufferedImage fit(BufferedImage src, int max) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) max / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage cur = draw(src, w, h);
        // bosqichma-bosqich kichraytirish sifatni saqlaydi
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return cur.getWidth() == tw && cur.getHeight() == th ? cur : draw(cur, tw, th);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] jpeg(BufferedImage img, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } catch (IOException e) {
            throw new BusinessException("Rasmni qayta ishlab bo'lmadi", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    /** keepKey dan boshqa barcha fayllarni o'chiradi (null bo'lsa hammasini). */
    private static void cleanup(Path dir, String keepKey) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> old = files.filter(f -> keepKey == null || !f.getFileName().toString().startsWith(keepKey)).toList();
            for (Path f : old) {
                Files.deleteIfExists(f);
            }
            if (keepKey == null) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            log.warn("Eski rasm fayllarini o'chirib bo'lmadi: {}", dir, e);
        }
    }
}
