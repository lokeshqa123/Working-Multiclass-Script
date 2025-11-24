package PDFautomation.pdfdownload.pages;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import PDFautomation.pdfdownload.util.WebDriverUtils;

public class CarePlanPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    private static final By CAREPLAN_TOOL = By.xpath("//span[@class='quick-tool-desc' and normalize-space()='CarePlan']");
    private static final By DIALOG = By.cssSelector("div.ui-dialog[role='dialog'], div.ui-dialog.ui-widget");
    private static final By PDF_LINK = By.xpath("//a[span[text()='PDF']]");
    private static final By PRINT_TAB = By.id("printTabs_2_center");
    private static final By SUBSET_SELECT = By.id("subsetIdSelect");
    private static final By FINISH_BTN = By.xpath("//div[@id='printTabs_2']//button[.//span[normalize-space(.)='Finish & PDF']]");
    private static final By PDF_BUTTON = By.xpath("//button[contains(@class,'standard_button') and @id='PDFButton']");

    private static final By[] IDT_CANDIDATES = new By[] {
        By.id("lp_quickview_3_center"),
        By.xpath("//li[contains(@id,'lp_quickview') and .//text()[contains(translate(., 'idt','IDT'),'IDT')]]"),
        By.xpath("//li[.//span and contains(translate(normalize-space(.//span/text()), 'idt','IDT'),'IDT')]")
    };

    public CarePlanPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public Path generateIdtPdfAndDownload(Path downloadsDir) throws InterruptedException, IOException {
        // open care plan with robust JS click and retry
        WebElement carePlanEl = wait.until(ExpectedConditions.presenceOfElementLocated(CAREPLAN_TOOL));
        int attempts = 0;
        while (true) {
            try {
                wait.until(ExpectedConditions.elementToBeClickable(carePlanEl));
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", carePlanEl);
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", carePlanEl);
                break;
            } catch (Exception e) {
                if (++attempts > 3) throw e;
                // refetch element and retry
                carePlanEl = wait.until(ExpectedConditions.presenceOfElementLocated(CAREPLAN_TOOL));
            }
        }
        WebDriverUtils.switchToNewWindow(driver);

        // wait dialog, switch to its iframe
        wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        boolean switched = WebDriverUtils.switchToFirstFrameInDialog(driver, wait);
        if (!switched) throw new RuntimeException("CarePlan dialog iframe missing");

        // find and click IDT
        WebElement idt = null;
        for (By b : IDT_CANDIDATES) {
            try {
                idt = new WebDriverWait(driver, Duration.ofSeconds(6)).until(ExpectedConditions.presenceOfElementLocated(b));
                if (idt != null) break;
            } catch (Exception ignored) {}
        }
        if (idt == null) throw new RuntimeException("IDT element not found in CarePlan frame");
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", idt);
            new WebDriverWait(driver, Duration.ofSeconds(6)).until(ExpectedConditions.elementToBeClickable(idt));
            idt.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", idt);
        }

        // IMPORTANT: PDF button is in the SAME modal iframe as IDT.
        // Capture current window to detect the newly opened one after clicking PDF.
        String parentWindow = driver.getWindowHandle();
        // While still inside this iframe, locate and click the PDF link.
        WebElement pdfLink = wait.until(ExpectedConditions.presenceOfElementLocated(PDF_LINK));
        try {
            wait.until(ExpectedConditions.visibilityOf(pdfLink));
            wait.until(ExpectedConditions.elementToBeClickable(pdfLink));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", pdfLink);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", pdfLink);
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", pdfLink);
        }

        // Wait for the new window to open and then switch to it
        new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(d -> d.getWindowHandles().size() > 1);
        for (String win : driver.getWindowHandles()) {
            if (!win.equals(parentWindow)) {
                driver.switchTo().window(win);
                break;
            }
        }

        wait.until(ExpectedConditions.elementToBeClickable(PRINT_TAB)).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(SUBSET_SELECT));
        new Select(driver.findElement(SUBSET_SELECT)).selectByVisibleText("IDT");
        WebElement button = driver.findElement(FINISH_BTN);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);

        Thread.sleep(1500); // brief pause before final PDF trigger
        wait.until(ExpectedConditions.elementToBeClickable(PDF_BUTTON)).click();

        long startTime = System.currentTimeMillis();
        return WebDriverUtils.waitForLatestPdf(downloadsDir, startTime, 60);
    }
}
