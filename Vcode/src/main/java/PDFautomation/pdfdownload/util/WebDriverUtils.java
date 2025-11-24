package PDFautomation.pdfdownload.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class WebDriverUtils {

    // Click using JS after waiting clickable
    public static void safeJsClick(WebDriver driver, WebDriverWait wait, By locator) {
        WebElement el = wait.until(ExpectedConditions.elementToBeClickable(locator));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    // Stale-safe click with retries and refetch by locator
    public static void staleSafeClick(WebDriver driver, WebDriverWait wait, By locator, int retries) {
        int attempts = 0;
        while (true) {
            try {
                WebElement el = wait.until(ExpectedConditions.elementToBeClickable(locator));
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
                el.click();
                return;
            } catch (StaleElementReferenceException e) {
                if (++attempts > retries) throw e;
            }
        }
    }

    public static boolean switchToFirstFrameInDialog(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("div.ui-dialog[role='dialog'], div.ui-dialog.ui-widget")));
            try {
                WebElement frame = dialog.findElement(By.tagName("iframe"));
                driver.switchTo().frame(frame);
                return true;
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        // fallback to any iframe
        List<WebElement> frames = driver.findElements(By.tagName("iframe"));
        if (!frames.isEmpty()) {
            driver.switchTo().frame(frames.get(0));
            return true;
        }
        return false;
    }

    public static void resetToDefault(WebDriver driver) {
        try { driver.switchTo().defaultContent(); } catch (Exception ignored) {}
    }

    public static void closeAllBut(WebDriver driver, String keepHandle) {
        for (String h : new ArrayList<>(driver.getWindowHandles())) {
            if (!h.equals(keepHandle)) {
                try {
                    driver.switchTo().window(h);
                    driver.close();
                } catch (Exception ignored) {}
            }
        }
        try { driver.switchTo().window(keepHandle); } catch (Exception ignored) {}
    }

    public static void closeAnyOpenDialog(WebDriver driver) {
        try {
            resetToDefault(driver);
            // Title bar close
            By titlebarClose = By.xpath(
                "//div[contains(@class,'ui-dialog') and @role='dialog' and not(contains(@style,'display: none'))]" +
                "//button[contains(@class,'ui-dialog-titlebar-close') or @title='Close' or @aria-label='Close']");
            List<WebElement> btns = driver.findElements(titlebarClose);
            if (!btns.isEmpty()) {
                try { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btns.get(0)); } catch (Exception ignored) {}
            }
            // Footer close
            By footerClose = By.xpath(
                "//div[contains(@class,'ui-dialog') and @role='dialog' and not(contains(@style,'display: none'))]" +
                "//button[normalize-space(.)='Close' or .//span[normalize-space(.)='Close']]"
            );
            btns = driver.findElements(footerClose);
            if (!btns.isEmpty()) {
                try { ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btns.get(0)); } catch (Exception ignored) {}
            }
            // ESC twice
            try {
                new Actions(driver).sendKeys(Keys.ESCAPE).perform();
                Thread.sleep(120);
                new Actions(driver).sendKeys(Keys.ESCAPE).perform();
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    public static void switchToNewWindow(WebDriver driver) {
        String current = driver.getWindowHandle();
        for (String handle : driver.getWindowHandles()) {
            if (!handle.equals(current)) {
                driver.switchTo().window(handle);
                break;
            }
        }
    }

    public static Path waitForLatestPdf(Path downloadsDir, long startTime, int maxSeconds) throws InterruptedException, IOException {
        int attempts = 0;
        while (attempts++ < maxSeconds) {
            Optional<Path> latest = Files.list(downloadsDir)
                    .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .filter(p -> lastModifiedMillis(p) > startTime)
                    .max(Comparator.comparingLong(WebDriverUtils::lastModifiedMillis));
            if (latest.isPresent()) return latest.get();
            Thread.sleep(1000);
        }
        throw new RuntimeException("PDF download not detected");
    }

    private static long lastModifiedMillis(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; }
    }
}
