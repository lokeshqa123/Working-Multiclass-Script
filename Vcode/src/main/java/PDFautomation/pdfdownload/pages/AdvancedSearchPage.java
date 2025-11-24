package PDFautomation.pdfdownload.pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.Keys;

import PDFautomation.pdfdownload.util.WebDriverUtils;

public class AdvancedSearchPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public AdvancedSearchPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void openAdvancedSearch() {
        // Open Advanced Participant Search modal (magnifier -> advanced)
        wait.until(ExpectedConditions.elementToBeClickable(By.id("single-search"))).click();
        WebElement advSearchBtn = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//button[@title='Advanced Participant Search']")
                )
        );
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", advSearchBtn);
        // Wait for the modal container to appear
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.ui-dialog[role='dialog']")));
    }

    public boolean searchAccountAndOpenFirstResult(String accountNumber) throws Exception {
        // Switch to iframe via multiple selectors
        boolean switched = false;
        String[] iframeSelectors = new String[] {
                "iframe[id*='Advanced']",
                "iframe[id*='Search']",
                "div.ui-dialog iframe",
                "iframe.ui-dialog-content",
                "iframe"
        };

        for (String sel : iframeSelectors) {
            try {
                if ("iframe".equals(sel)) {
                    List<WebElement> frames = driver.findElements(By.cssSelector("div.ui-dialog[role='dialog'] iframe"));
                    if (frames != null && !frames.isEmpty()) {
                        driver.switchTo().frame(frames.get(0));
                        switched = true;
                        break;
                    }
                    List<WebElement> allFrames = driver.findElements(By.tagName("iframe"));
                    if (!allFrames.isEmpty()) {
                        driver.switchTo().frame(allFrames.get(0));
                        switched = true;
                        break;
                    }
                } else {
                    try {
                        wait.withTimeout(Duration.ofSeconds(5))
                            .until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.cssSelector(sel)));
                        switched = true;
                        break;
                    } finally {
                        wait.withTimeout(Duration.ofSeconds(30));
                    }
                }
            } catch (Exception e) {
                // ignore and try next selector
            }
        }

        if (!switched) {
            System.out.println("Info: No iframe switch performed; proceeding in main DOM context.");
        }

        try {
            // Select Membership -> All
            By[] membershipCandidates = new By[] {
                    By.xpath("//form[@name='form_patientSearch']//select[@name='membershipstate']"),
                    By.name("membershipstate"),
                    By.xpath("//label[contains(normalize-space(.),'Membership')]/following::select[1]"),
                    By.cssSelector("div.ui-dialog select[name='membershipstate']"),
                    By.xpath("(//select)[position() < 10 and contains(@name,'membership')]")
            };

            WebElement selElem = null;
            for (By candidate : membershipCandidates) {
                try {
                    selElem = wait.until(ExpectedConditions.presenceOfElementLocated(candidate));
                    if (selElem != null) {
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (selElem == null) {
                throw new RuntimeException("Membership select not found with any candidate locator.");
            }

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", selElem);

            boolean selected = false;
            try {
                Select dropdown = new Select(selElem);
                dropdown.selectByVisibleText("All");
                selected = true;
            } catch (Exception e) {
                try {
                    selElem.click();
                    WebElement opt = selElem.findElement(By.xpath(".//option[normalize-space()='All']"));
                    new Actions(driver).moveToElement(opt).click().perform();
                    selected = true;
                } catch (Exception ignore) {
                    // fall through
                }
            }

            if (!selected) {
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                        selElem, "All");
            }

            try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            // Fill account
            By[] accountFieldCandidates = new By[] {
                    By.xpath("//form[@name='form_patientSearch']//input[@name='accountnumber']"),
                    By.name("accountnumber"),
                    By.xpath("//label[contains(normalize-space(.),'Acct')]/following::input[1]"),
                    By.cssSelector("input[name='accountnumber']"),
                    By.xpath("//input[@type='text' and contains(@name,'account')]")
            };

            WebElement accountField = null;
            for (By candidate : accountFieldCandidates) {
                try {
                    accountField = wait.until(ExpectedConditions.presenceOfElementLocated(candidate));
                    if (accountField != null) {
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (accountField == null) {
                throw new RuntimeException("Account number field not found with any candidate locator.");
            }

            accountField.clear();
            accountField.sendKeys(accountNumber);

            // Click Find
            By findButton = By.xpath("//button[normalize-space()='Find' or normalize-space()='Search'] | //button[@id='FindButton']");
            WebElement findBtn = wait.until(ExpectedConditions.elementToBeClickable(findButton));
            findBtn.click();

            // After Find: still inside iframe
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
            By resultsRowsSelector = By.cssSelector(
                    "table#patientSearchTablesorter tbody tr.patient-result-row, " +
                    "table.tablesorter tbody tr.patient-result-row, " +
                    "table.tablesorter tbody tr"
            );
            List<WebElement> rows = shortWait.until(
                    ExpectedConditions.numberOfElementsToBeMoreThan(resultsRowsSelector, 0)
            );

            if (rows == null || rows.isEmpty()) {
                driver.switchTo().defaultContent();
                return false;
            }

            WebElement firstRow = rows.get(0);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", firstRow);
            firstRow.click();

            driver.switchTo().defaultContent();
            return true;
        } finally {
            try { driver.switchTo().defaultContent(); } catch (Exception ignore) {}
        }
    }
}
