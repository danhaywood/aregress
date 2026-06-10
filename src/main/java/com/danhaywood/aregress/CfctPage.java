package com.danhaywood.aregress;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

public class CfctPage {

    // TODO (task 3.5): Discover exact selectors by inspecting the running cfct Vaadin app.
    // Vaadin apps may require shadow-DOM piercing; use browser devtools on localhost:10010.
    private static final String REFRESH_SELECTOR = "vaadin-button:has-text('Refresh')";

    // The panel is always present; tabs appear only when differences exist.
    private static final String DIFFERENCE_TABS_SELECTOR = "vaadin-tabsheet vaadin-tab";

    private final Page page;

    public CfctPage(Page page) {
        this.page = page;
    }

    public void navigateTo(String url) {
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickRefresh() {
        page.locator(REFRESH_SELECTOR).click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public boolean hasDifferenceTabs() {
        return page.locator(DIFFERENCE_TABS_SELECTOR).count() > 0;
    }
}
