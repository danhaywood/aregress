package com.danhaywood.aregress;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wraps the cfct ("Command Footprint Comparison Tool") Vaadin app.
 *
 * cfct exposes stable {@code data-testid} attributes, used here in preference to brittle Vaadin CSS.
 * Flow per comparison: {@link #refresh()} → {@link #selectAllTables()} → {@link #compare()} →
 * {@link #downloadComparison()}. Difference detection is via the JSON exported by the "Download"
 * action (top-level {@code hasDifferences}), which is far more robust than scraping the results tabs.
 *
 * NOTE: the efficient per-command "footprint" selection (which cfct auto-selects on refresh in an
 * interactive session) does not trigger under headless automation, so this drives a full-database
 * compare via "Select all". Correct, but slower; revisit once the footprint-load event is known.
 */
public class CfctPage {

    private final Page page;
    private final String password;

    public CfctPage(Page page, String password) {
        this.page = page;
        this.password = password;
    }

    /** Navigate to cfct and establish the database connection (Refresh/Compare are disabled until then). */
    public void login(String url) {
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        page.getByTestId("login-password").locator("input").fill(password);
        page.getByTestId("login-submit").click();
        // Wait until the connection is live (Refresh becomes enabled).
        waitUntilEnabled(page.getByTestId("command-filter-refresh"), 30_000);
    }

    /** Refresh the command list from the databases (auto-selects the most-recently executed command). */
    public void refresh() {
        page.getByTestId("command-filter-refresh").click();
        page.waitForTimeout(2500);
    }

    /** Select every table for comparison (full-database compare) and wait until Compare is enabled. */
    public void selectAllTables() {
        page.getByTestId("table-select-all-checkbox").click();
        waitUntilEnabled(page.getByTestId("compare-button"), 15_000);
    }

    /** Run the comparison and block until cfct reports completion. */
    public void compare() {
        page.getByTestId("compare-button").click();
        waitForCompareComplete();
    }

    /** Click "Download", capture the exported JSON, and parse it into a {@link ComparisonResult}. */
    public ComparisonResult downloadComparison() {
        Locator trigger = page.locator("vaadin-button:has-text(\"Download\"), a:has-text(\"Download\")").first();
        Download download = page.waitForDownload(trigger::click);
        try {
            Path tmp = Files.createTempFile("aregress-comparison", ".json");
            download.saveAs(tmp);
            return ComparisonResult.parse(tmp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to capture cfct comparison download", e);
        }
    }

    private void waitForCompareComplete() {
        long deadline = System.currentTimeMillis() + 180_000; // full-DB compare can take a while
        String prev = "";
        int stable = 0;
        while (System.currentTimeMillis() < deadline) {
            page.waitForTimeout(1000);
            String summary = progressSummary();
            if (summary.equals(prev)) {
                stable++;
            } else {
                stable = 0;
                prev = summary;
            }
            if (summary.toLowerCase().contains("complete") && stable >= 2) {
                return;
            }
        }
    }

    private String progressSummary() {
        Locator s = page.getByTestId("comparison-progress-summary");
        return s.count() > 0 ? s.innerText().trim() : "";
    }

    private void waitUntilEnabled(Locator locator, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Object disabled = locator.evaluate("e => e.hasAttribute('disabled')");
            if (Boolean.FALSE.equals(disabled)) {
                return;
            }
            page.waitForTimeout(500);
        }
    }
}
