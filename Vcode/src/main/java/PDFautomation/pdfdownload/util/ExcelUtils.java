package PDFautomation.pdfdownload.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class ExcelUtils {

    public static List<String> readAccountNumbers(
            String excelPath,
            String sheetName,
            int startRowIndex,
            int colIndex,
            int maxCount) throws IOException {
        List<String> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            DataFormatter formatter = new DataFormatter();
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new RuntimeException("Sheet '" + sheetName + "' not found in " + excelPath);
            }

            int headerRow = sheet.getFirstRowNum();
            int dataStart = headerRow + 1; // row after header
            int start = Math.max(startRowIndex, dataStart);
            int last = sheet.getLastRowNum();

            for (int r = start; r <= last && list.size() < maxCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(colIndex);
                if (cell == null) continue;
                String raw = formatter.formatCellValue(cell);
                if (raw == null) continue;
                raw = raw.trim();
                if (raw.isEmpty()) continue;

                String normalized = raw.replaceAll("\\s+", "").toLowerCase();
                if (normalized.equals("account") || normalized.equals("accountnumber")) {
                    continue;
                }

                if (raw.matches("^\\d+(?:\\.0+)?$")) {
                    raw = raw.replaceAll("\\.0+$", "");
                    list.add(raw);
                }
            }
        }
        Set<String> dedup = new LinkedHashSet<>(list);
        return new ArrayList<>(dedup);
    }
}
