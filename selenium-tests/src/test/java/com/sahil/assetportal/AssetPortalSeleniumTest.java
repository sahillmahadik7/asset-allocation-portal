
package com.sahil.assetportal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.openqa.selenium.chrome.ChromeOptions;

class AssetPortalSeleniumTest {

    private WebDriver driver;
    private WebDriverWait wait;
    private String assetCode;

    private static final String BASE_URL =
        System.getProperty("app.url", "http://localhost:8081/");

    @BeforeEach
    void setUp() {
        assetCode = "SEL-" +
                UUID.randomUUID().toString().substring(0, 8);

        ChromeOptions options = new ChromeOptions();
options.addArguments(
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--window-size=1920,1080"
);
driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        driver.manage().window().maximize();
        driver.get(BASE_URL);

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("assetTableBody")));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // Capture screenshots when a test fails.
    @RegisterExtension
    TestWatcher screenshotWatcher = new TestWatcher() {

        @Override
        public void testFailed(
                ExtensionContext context,
                Throwable cause) {

            if (driver == null) {
                return;
            }

            try {
                Path screenshotDirectory =
                        Path.of("target", "screenshots");

                Files.createDirectories(screenshotDirectory);

                String fileName = context.getDisplayName()
                        .replaceAll("[^a-zA-Z0-9.-]", "_");

                Path screenshotPath =
                        screenshotDirectory.resolve(fileName + ".png");

                byte[] screenshot =
                        ((TakesScreenshot) driver)
                                .getScreenshotAs(OutputType.BYTES);

                Files.write(screenshotPath, screenshot);

                System.out.println(
                        "Failure screenshot saved: " +
                                screenshotPath.toAbsolutePath());

            } catch (IOException | RuntimeException e) {
                System.err.println(
                        "Could not save screenshot: " + e.getMessage());
            }
        }
    };

    private WebElement visibleElement(By locator) {
        return wait.until(
                ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private WebElement clickableElement(By locator) {
        return wait.until(
                ExpectedConditions.elementToBeClickable(locator));
    }

    private By assetRowLocator() {
        return By.xpath(
                "//tr[.//*[contains(text(),'" + assetCode + "')]]");
    }

    private WebElement assetRow() {
        return visibleElement(assetRowLocator());
    }

    private void addTestAsset() {
        clickableElement(
                By.cssSelector(".topbar .primary-btn")).click();

        visibleElement(By.id("assetCode")).sendKeys(assetCode);

        new Select(visibleElement(By.id("assetType")))
                .selectByVisibleText("Laptop");

        visibleElement(By.id("name"))
                .sendKeys("Selenium Test Laptop");

        visibleElement(By.id("brand")).sendKeys("Dell");
        visibleElement(By.id("model")).sendKeys("Latitude");
        visibleElement(By.id("serialNumber")).sendKeys(assetCode);

        new Select(visibleElement(By.id("status")))
                .selectByVisibleText("Available");

        visibleElement(By.id("location")).sendKeys("Test Lab");

        clickableElement(
                By.cssSelector("#assetForm button[type='submit']"))
                .click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                assetRowLocator()));
    }

    @Test
    void dashboardLoads() {
        assertTrue(driver.getPageSource().contains("Asset Dashboard"));
        assertTrue(driver.getPageSource().contains("Asset Inventory"));

        assertTrue(visibleElement(By.id("totalAssets")).isDisplayed());
        assertTrue(visibleElement(By.id("assetTableBody")).isDisplayed());
    }

    @Test
    void assetCanBeCreated() {
        addTestAsset();

        assertTrue(assetRow().getText().contains(assetCode));
        assertTrue(assetRow().getText().contains("Selenium Test Laptop"));
    }

    @Test
    void assetCanBeSearched() {
        addTestAsset();

        WebElement search = visibleElement(By.id("searchInput"));
        search.sendKeys(assetCode);

        assertTrue(assetRow().getText().contains(assetCode));
    }

    @Test
    void assetCanBeEdited() {
        addTestAsset();

        assetRow()
                .findElement(By.cssSelector(".edit-btn"))
                .click();

        WebElement nameField = visibleElement(By.id("name"));
        nameField.clear();
        nameField.sendKeys("Updated Selenium Laptop");

        clickableElement(
                By.cssSelector("#assetForm button[type='submit']"))
                .click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                assetRowLocator(), "Updated Selenium Laptop"));

        assertTrue(
                assetRow().getText().contains("Updated Selenium Laptop"));
    }

    @Test
    void assetCanBeDeleted() {
        addTestAsset();

        assetRow()
                .findElement(By.cssSelector(".delete-btn"))
                .click();

        Alert alert = wait.until(
                ExpectedConditions.alertIsPresent());

        alert.accept();

        wait.until(ExpectedConditions.invisibilityOfElementLocated(
                assetRowLocator()));

        assertTrue(driver.findElements(assetRowLocator()).isEmpty());
    }
}
