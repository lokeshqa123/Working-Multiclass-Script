package PDFautomation.pdfdownload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.Select;

import io.github.bonigarcia.wdm.WebDriverManager;
import PDFautomation.pdfdownload.util.WebDriverUtils;
import PDFautomation.pdfdownload.pages.AdvancedSearchPage;
import PDFautomation.pdfdownload.pages.CarePlanPage;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class PDFautomation {

    private static final String BASE_URL = "https://ippne.truchart.com/truchart_app/Home.jsp";
    private static final String USERNAME = "CDWadmin1";
    private static final String PASSWORD = "CDWadmin1";
    private static final String DEFAULT_EXCEL = "downloads/CDW - Care Plan Work.xlsx";

    public static void main(String[] args) throws Exception {
        String excelPath = args.length > 0 ? args[0] : DEFAULT_EXCEL;
        Path downloadsDir = Paths.get(System.getProperty("user.home"), "Downloads");

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("plugins.always_open_pdf_externally", true);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.default_directory", downloadsDir.toString());
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        driver.manage().window().maximize();

        String accountNumber = null;

        try {
            System.out.println("Started (Excel: " + excelPath + ")");
            driver.get(BASE_URL);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("tru_username"))).sendKeys(USERNAME);
            driver.findElement(By.id("j_password")).sendKeys(PASSWORD);
            wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//form[@name='login']//button[.//span[normalize-space()='Login']]")
            )).click();

            // Optional screenshot as in source-of-truth
            try {
                Files.createDirectories(Path.of("C:/screenshots"));
                String filename = "login_test_" + System.currentTimeMillis() + ".png";
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(src.toPath(), Path.of("C:/screenshots", filename));
                System.out.println("Screenshot saved at: C:/screenshots/" + filename);
            } catch (Exception e) {
                System.out.println("Screenshot capture FAILED: " + e.getMessage());
            }

            // Advanced Search via page object with same locators/steps
            AdvancedSearchPage searchPage = new AdvancedSearchPage(driver, wait);
            searchPage.openAdvancedSearch();

            // Read a single account, process it, then return. We can iterate to next later.
            accountNumber = readAccountNumber(excelPath, 3, 1);
            boolean opened = searchPage.searchAccountAndOpenFirstResult(accountNumber);
            if (!opened) {
                System.out.println("No results for account: " + accountNumber + "; exiting.");
                return;
            }

            // CarePlan flow via dedicated page using the same locators/logic
            CarePlanPage carePlan = new CarePlanPage(driver, wait);
            Path downloaded = carePlan.generateIdtPdfAndDownload(downloadsDir);

            Path renamed = downloadsDir.resolve("account_" + accountNumber + ".pdf");
            Files.move(downloaded, renamed);
            System.out.println("Downloaded: " + downloaded);
            System.out.println("Renamed: " + renamed);
            System.out.println("Done");
        } finally {
            try { driver.quit(); } catch (Exception ignore) {}
        }
    }

    // Excel helpers copied from source-of-truth
    private static String readAccountNumber(String excelPath, int rowIndex, int colIndex) throws IOException {
        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheet("IPO");
            if (sheet == null) {
                throw new RuntimeException("Sheet 'IPO' not found in " + excelPath);
            }
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                throw new RuntimeException("Missing row " + (rowIndex + 1) + " in sheet IPO");
            }
            Cell cell = row.getCell(colIndex);
            if (cell == null) {
                throw new RuntimeException("Missing cell at row " + (rowIndex + 1) + ", col " + (colIndex + 1) + ")");
            }

            DataFormatter formatter = new DataFormatter();
            String raw = formatter.formatCellValue(cell);
            if (raw == null) {
                throw new RuntimeException("Account number cell is empty (row " + (rowIndex + 1) + ", col " + (colIndex + 1) + ")");
            }
            raw = raw.trim();

            // Remove trailing ".0" for integer-like numeric values
            if (raw.matches("^-?\\d+\\.0+$")) {
                raw = raw.replaceAll("\\.0+$", "");
            }
            return raw;
        }
    }

    private static String readAccountNumber(String excelPath) throws IOException {
        return readAccountNumber(excelPath, 1, 0);
    }
}
