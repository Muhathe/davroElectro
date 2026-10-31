package univer.university.component;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import univer.university.exception.BusinessException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Excel (.xlsx) o'qish va shablon yaratish (Apache POI). */
@Component
public class ExcelReader {

    /** Ro'yxatli ustun shablonda shuncha qatorgacha ishlaydi. */
    private static final int TEMPLATE_ROWS = 20000;

    /**
     * Excel qatori: raqami (1 dan, sarlavha 1-qator), sarlavha bo'yicha qiymatlar va son katakli ustunlar.
     * Excel sonni 15 xonagacha saqlaydi, shuning uchun uzun kod son katakda aniqligini yo'qotgan bo'ladi.
     */
    public record ExcelRow(int number, Map<String, String> cells, Set<String> numeric) {
        public String get(String key) {
            String v = cells.get(key);
            return v == null ? "" : v.trim();
        }

        public boolean isNumeric(String key) {
            return numeric.contains(key);
        }
    }

    /**
     * To'liq shablon ustuni.
     * @param text    katak matn formatida (kodlar: bosh nol va uzun raqam buzilmasligi uchun)
     * @param options bo'sh bo'lmasa, katakda tanlash ro'yxati
     */
    public record TemplateColumn(String header, boolean required, boolean text, List<String> options, String note, String example) {
    }

    /** Sarlavha nomini solishtirish uchun normallashtirish: kichik harf, faqat harf va raqam. */
    public static String normalize(String header) {
        if (header == null) {
            return "";
        }
        return header.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static CellType type(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    /** Katak qiymati: son formatidan qat'i nazar aniq (12,000 -> 12000, barcode -> to'liq raqam), formula — natijasi. */
    private static String text(Cell cell, DataFormatter fmt) {
        boolean formula = cell.getCellType() == CellType.FORMULA;
        CellType type = type(cell);
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
                Set<String> numeric = new HashSet<>();
                boolean any = false;
                for (int c = 0; c < keys.size(); c++) {
                    Cell cell = row.getCell(c);
                    String v = cell == null ? "" : text(cell, fmt);
                    if (!v.isEmpty()) {
                        any = true;
                    }
                    cells.put(keys.get(c), v);
                    if (cell != null && type(cell) == CellType.NUMERIC) {
                        numeric.add(keys.get(c));
                    }
                }
                if (any) {
                    rows.add(new ExcelRow(r + 1, cells, numeric));
                }
            }
        } catch (IOException e) {
            throw BusinessException.badRequest("Excel faylni o'qib bo'lmadi");
        }
        return rows;
    }

    /**
     * To'liq shablon. Birinchi varaq toza (faqat sarlavha): namuna qatori tasodifan haqiqiy tovar bo'lib kirib qolmasligi uchun
     * ustunlar izohi, namuna va qoidalar ikkinchi varaqda. Import faqat birinchi varaqni o'qiydi.
     */
    public byte[] fullTemplate(String sheetName, List<TemplateColumn> columns, List<String> rules) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle textStyle = wb.createCellStyle();
            textStyle.setDataFormat(wb.createDataFormat().getFormat("@"));
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);
            CellStyle requiredStyle = wb.createCellStyle();
            requiredStyle.setFont(bold);
            requiredStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            requiredStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sheet = wb.createSheet(sheetName);
            sheet.createFreezePane(0, 1);
            Row h = sheet.createRow(0);
            DataValidationHelper validation = sheet.getDataValidationHelper();
            for (int i = 0; i < columns.size(); i++) {
                TemplateColumn col = columns.get(i);
                Cell cell = h.createCell(i);
                cell.setCellValue(col.header());
                cell.setCellStyle(col.required() ? requiredStyle : headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
                if (col.text()) {
                    sheet.setDefaultColumnStyle(i, textStyle);
                }
                if (!col.options().isEmpty()) {
                    DataValidation list = validation.createValidation(
                            validation.createExplicitListConstraint(col.options().toArray(new String[0])),
                            new CellRangeAddressList(1, TEMPLATE_ROWS, i, i));
                    list.setSuppressDropDownArrow(true);
                    list.setShowErrorBox(true);
                    sheet.addValidationData(list);
                }
            }

            Sheet help = wb.createSheet("Yo'riqnoma");
            help.setColumnWidth(0, 18 * 256);
            help.setColumnWidth(1, 11 * 256);
            help.setColumnWidth(2, 80 * 256);
            help.setColumnWidth(3, 20 * 256);
            Row top = help.createRow(0);
            List<String> titles = List.of("Ustun", "Majburiy", "Izoh", "Namuna");
            for (int i = 0; i < titles.size(); i++) {
                Cell cell = top.createCell(i);
                cell.setCellValue(titles.get(i));
                cell.setCellStyle(headerStyle);
            }
            int r = 1;
            for (TemplateColumn col : columns) {
                Row row = help.createRow(r++);
                row.createCell(0).setCellValue(col.header());
                row.createCell(1).setCellValue(col.required() ? "ha" : "yo'q");
                row.createCell(2).setCellValue(col.note());
                row.createCell(3).setCellValue(col.example());
            }
            r++;
            Cell title = help.createRow(r++).createCell(0);
            title.setCellValue("Qoidalar");
            title.setCellStyle(headerStyle);
            for (String rule : rules) {
                help.createRow(r++).createCell(0).setCellValue(rule);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Shablon yaratib bo'lmadi", e);
        }
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
