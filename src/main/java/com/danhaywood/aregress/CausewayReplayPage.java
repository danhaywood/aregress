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
    // The "Pending Or Failed" collection's rows. Scoped to this collection's stable Wicket id so we never
    // read the separate "Succeeded Or Excluded" table. Column order is NOT relied upon — each cell is
    // addressed by its per-property CSS class (Causeway renders one on every column td), so adding or
    // reordering columns (e.g. the Target column) does not break these selectors.
    private static final String PENDING_ROWS = "#collection-pendingOrFailed tbody tr";
    // The Replay State badge in each command row.
    private static final String STATE_BADGE_SELECTOR = ".fragment-compact-badge";
    // Per-column compact-value labels, addressed by the column's stable property CSS class.
    private static final String MEMBER_LABEL_SELECTOR =
            "td.isis-isis-ext-commandLog-ReplayableCommand-member .fragment-compact-label";
    private static final String TARGET_LABEL_SELECTOR =
            "td.isis-isis-ext-commandLog-ReplayableCommand-target .fragment-compact-label";
    // The command's interaction id, from its entity link href (…ReplayableCommand:<interactionId>).
    private static final Pattern REPLAYABLE_COMMAND_ID =
            Pattern.compile("ReplayableCommand:([0-9a-fA-F-]{36})");
    // The two logical-member-identifier prefixes that mark a "navigate to one of" command
    // (the second covers the legacy Apache Isis namespace prior to the Causeway rename).
    private static final String NAVIGATE_TO_ONE_OF_PREFIX_CAUSEWAY = "__causeway_navigate_to_one_of_";
    private static final String NAVIGATE_TO_ONE_OF_PREFIX_ISIS = "__isis_navigate_to_one_of_";

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

    /** The oldest command's row (head of the Pending Or Failed collection), or an empty locator. */
    private Locator firstPendingRow() {
        return page.locator(PENDING_ROWS).first();
    }

    /**
     * Member of the oldest command — the "Member" column value (e.g. {@code changeName}, or the raw
     * {@code __isis_navigate_to_one_of_communicationChannels} for a navigate-to-one-of command), or
     * null if the collection is empty. Read by the Member column's property CSS class.
     */
    public String oldestCommandMember() {
        Locator firstRow = firstPendingRow();
        if (firstRow.count() == 0) {
            return null;
        }
        Locator label = firstRow.locator(MEMBER_LABEL_SELECTOR).first();
        return label.count() > 0 ? label.innerText().trim() : null;
    }

    /**
     * Interaction id of the oldest command (the one "Replay Or Retry Next" will execute), or null if
     * the collection is empty. Read from the row's command entity link, whose href is
     * {@code ./isis.ext.commandLog.ReplayableCommand:<interactionId>}.
     */
    public String oldestCommandInteractionId() {
        Locator firstRow = firstPendingRow();
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

    /**
     * Logical member identifier of the oldest command — the value of the "Member" column, which for a
     * navigate-to-one-of command is the raw identifier carrying the {@code __causeway_navigate_to_one_of_}
     * or {@code __isis_navigate_to_one_of_} prefix. Same source as {@link #oldestCommandMember()}.
     */
    public String oldestCommandLogicalMemberIdentifier() {
        return oldestCommandMember();
    }

    /**
     * True if the oldest command is a "navigate to one of" command, i.e. its Member value carries the
     * {@code __causeway_navigate_to_one_of_} or (legacy) {@code __isis_navigate_to_one_of_} prefix.
     */
    public boolean isOldestCommandNavigateToOneOf() {
        return isNavigateToOneOf(oldestCommandLogicalMemberIdentifier());
    }

    /** Whether a logical member identifier carries either navigate-to-one-of prefix. */
    public static boolean isNavigateToOneOf(String logicalMemberIdentifier) {
        return logicalMemberIdentifier != null
                && (logicalMemberIdentifier.contains(NAVIGATE_TO_ONE_OF_PREFIX_CAUSEWAY)
                    || logicalMemberIdentifier.contains(NAVIGATE_TO_ONE_OF_PREFIX_ISIS));
    }

    /**
     * Target bookmark of the oldest command (e.g. {@code sharedkernel.party.Organisation:6}), or null
     * if the collection is empty or the Target column is absent. The "Target" column renders the
     * bookmark as plain text; read by the Target column's property CSS class.
     */
    public String oldestCommandTargetBookmark() {
        Locator firstRow = firstPendingRow();
        if (firstRow.count() == 0) {
            return null;
        }
        Locator label = firstRow.locator(TARGET_LABEL_SELECTOR).first();
        if (label.count() == 0) {
            return null;
        }
        String text = label.innerText().trim();
        return text.isEmpty() ? null : text;
    }

    /** Replay State of the oldest command: "Pending", "Failed", "Ok", or null if the collection is empty. */
    public String oldestReplayState() {
        Locator firstRow = firstPendingRow();
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
