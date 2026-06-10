package com.danhaywood.aregress;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

public class CausewayReplayPage {

    // TODO (task 3.2): Discover exact selector by inspecting the running Causeway Wicket app.
    // Navigate to the CommandReplayManager page and use browser devtools to find the button.
    private static final String REPLAY_NEXT_SELECTOR = "a:has-text('Replay Next')";

    private final Page page;

    public CausewayReplayPage(Page page) {
        this.page = page;
    }

    public void navigateTo(String url) {
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public boolean isReplayNextEnabled() {
        Locator button = page.locator(REPLAY_NEXT_SELECTOR);
        return button.count() > 0 && button.isEnabled();
    }

    public void clickReplayNext() {
        page.locator(REPLAY_NEXT_SELECTOR).click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }
}
