package univer.university.component;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import univer.university.exception.BusinessException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Excel (.xlsx) o'qish va shablon yaratish (Apache POI). */
@Component
public class ExcelReader {

    /** Excel qatori: raqami (1 dan, sarlavha 1-qator) va sarlavha bo'yicha qiymatlar. */
    public record ExcelRow(int number, Map<String, String> cells) {
        public String get(String key) {
            String v = cells.get(key);
            return v == null ? "" : v.trim();
        }
    }

    /** Sarlavha nomini solishtirish uchun normallashtirish: kichik harf, faqat harf va raqam. */
    public static String normalize(String header) {
        if (header == null) {
            return "";
        }
        return header.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Katak qiymati: son formatidan qat'i nazar aniq (12,000 -> 12000, barcode -> to'liq raqam), formula — natijasi. */
    private static String text(Cell cell, DataFormatter fmt) {
        boolean formula = cell.getCellType() == CellType.FORMULA;
        CellType type = formula ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        return formula ? "" : fmt.formatCellValue(cell).trim();
    }

    /** @param required majburiy ustunlar (normalize qilingan nomlar) */
    public List<ExcelRow> read(MultipartFile file, String... required) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Fayl bo'sh");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw BusinessException.badRequest("Faqat .xlsx fayl qabul qilinadi");
        }
        DataFormatter fmt = new DataFormatter(Locale.US);
        List<ExcelRow> rows = new ArrayList<>();
        try (InputStream in = file.getInputStream(); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw BusinessException.badRequest("Birinchi qatorda ustun nomlari bo'lishi kerak");
            }
            List<String> keys = new ArrayList<>();
            // indeks bo'yicha: bo'sh sarlavha katagi keyingi ustunlarni surib yubormasligi uchun
            for (int c = 0; c < header.getLastCellNum(); c++) {
                Cell cell = header.getCell(c);
                keys.add(cell == null ? "" : normalize(fmt.formatCellValue(cell)));
            }
            for (String col : required) {
                if (!keys.contains(col)) {
                    throw BusinessException.badRequest("Faylda '" + col + "' ustuni yo'q");
                }
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> cells = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < keys.size(); c++) {
                    Cell cell = row.getCell(c);
                    String v = cell == null ? "" : text(cell, fmt);
                    if (!v.isEmpty()) {
                        any = true;
                    }
                    cells.put(keys.get(c), v);
                }
                if (any) {
                    rows.add(new ExcelRow(r + 1, cells));
                }
            }
        } catch (IOException e) {
            throw BusinessException.badRequest("Excel faylni o'qib bo'lmadi");
        }
        return rows;
    }

    /** Sarlavha va bitta namuna qatori bor bo'sh shablon. */
    public byte[] template(String sheetName, List<String> headers, List<String> example) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            // birinchi ustun (barcode) matn: Excel uzun raqamni 4.78E+12 ko'rinishiga o'tkazmasligi uchun
            CellStyle textStyle = wb.createCellStyle();
            textStyle.setDataFormat(wb.createDataFormat().getFormat("@"));
            sheet.setDefaultColumnStyle(0, textStyle);
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                h.createCell(i).setCellValue(headers.get(i));
                sheet.setColumnWidth(i, 18 * 256);
            }
            Row ex = sheet.createRow(1);
            for (int i = 0; i < example.size(); i++) {
                ex.createCell(i).setCellValue(example.get(i));
            }
            ex.getCell(0).setCellStyle(textStyle);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Shablon yaratib bo'lmadi", e);
        }
    }
}
