package com.danhaywood.aregress;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps the Apache Causeway (Wicket) CommandReplayManager page of one app instance.
 *
 * Selectors were discovered by inspecting the running app:
 * - The replay action is the anchor labelled "Replay Or Retry Next" ("Executes the oldest command").
 *   It is a Wicket AJAX {@code <a>} and is never HTML-disabled, so loop control is driven by the
 *   pending-commands collection, NOT by a button-enabled check.
 * - Each command row's "Replay State" column holds a badge reading Pending / Failed / Ok. A
 *   successfully-replayed command leaves the collection; a failed one stays at the head (the action
 *   is "Replay <b>Or Retry</b> Next").
 */
public class CausewayReplayPage {

    // Stable text selector; the Wicket element id (e.g. actionLink397) is dynamic and must not be used.
    private static final String REPLAY_NEXT_SELECTOR = "a:has-text(\"Replay Or Retry Next\")";
    // The Replay State badge in each command row.
    private static final String STATE_BADGE_SELECTOR = ".fragment-compact-badge";
    // The command's interaction id, from its entity link href (…ReplayableCommand:<interactionId>).
    private static final Pattern REPLAYABLE_COMMAND_ID =
            Pattern.compile("ReplayableCommand:([0-9a-fA-F-]{36})");

    private final Page page;
    private final String username;
    private final String password;

    public CausewayReplayPage(Page page, String username, String password) {
        this.page = page;
        this.username = username;
        this.password = password;
    }

    /** Navigate to the replay URL, authenticating via the Spring Security form login if redirected. */
    public void navigateTo(String url) {
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        if (page.url().contains("/login")) {
            page.fill("#username", username);
            page.fill("#password", password);
            page.locator("button[type=submit], input[type=submit]").first().click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);
        }
    }

    /**
     * True while there is at least one command still to replay (Pending or Failed) at the head of the
     * pending collection. Successfully-replayed commands leave the collection, so this becomes false
     * once every command has replayed OK.
     */
    public boolean hasPendingCommands() {
        String state = oldestReplayState();
        return "Pending".equalsIgnoreCase(state) || "Failed".equalsIgnoreCase(state);
    }

    /** Member (action) name of the oldest command — the one "Replay Or Retry Next" will execute. */
    public String oldestCommandMember() {
        Locator firstRow = page.locator("table").first().locator("tbody tr").first();
        if (firstRow.count() == 0) {
            return null;
        }
        Locator cells = firstRow.locator("td");
        return cells.count() > 3 ? cells.nth(3).innerText().trim() : null;
    }

    /**
     * Interaction id of the oldest command (the one "Replay Or Retry Next" will execute), or null if
     * the collection is empty. Read from the row's command entity link, whose href is
     * {@code ./isis.ext.commandLog.ReplayableCommand:<interactionId>}.
     */
    public String oldestCommandInteractionId() {
        Locator firstRow = page.locator("table").first().locator("tbody tr").first();
        if (firstRow.count() == 0) {
            return null;
        }
        Locator link = firstRow.locator("a[href*=\"ReplayableCommand:\"]").first();
        if (link.count() == 0) {
            return null;
        }
        String href = link.getAttribute("href");
        if (href == null) {
            return null;
        }
        Matcher m = REPLAYABLE_COMMAND_ID.matcher(href);
        return m.find() ? m.group(1) : null;
    }

    /** Replay State of the oldest command: "Pending", "Failed", "Ok", or null if the collection is empty. */
    public String oldestReplayState() {
        Locator firstRow = page.locator("table").first().locator("tbody tr").first();
        if (firstRow.count() == 0) {
            return null;
        }
        Locator badge = firstRow.locator(STATE_BADGE_SELECTOR).first();
        return badge.count() > 0 ? badge.innerText().trim() : null;
    }

    /** Click "Replay Or Retry Next" (executes the oldest command) and wait for the AJAX update to settle. */
    public void replayNext() {
        page.locator(REPLAY_NEXT_SELECTOR).first().click();
        waitForCompletion();
    }

    private void waitForCompletion() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // Causeway updates the table via a Wicket AJAX response; give the DOM a moment to re-render.
        page.waitForTimeout(1500);
    }
}
