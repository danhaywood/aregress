package com.danhaywood.aregress;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * The aregress command: replay recorded commands on two Causeway apps in lockstep and compare their
 * databases via cfct after each command, stopping on the first replay failure or database divergence.
 *
 * A Spring-managed bean (instantiated via {@code PicocliSpringFactory}) so that {@link AregressProperties}
 * can be injected. For the externalized settings, the CLI option wins when supplied, otherwise the
 * configured value (or its bundled default) is used. Secrets are CLI/prompt only.
 */
@Command(
        name = "aregress",
        mixinStandardHelpOptions = true,
        description = "Automates regression testing by replaying commands on two Causeway app instances "
                + "in lockstep and comparing their databases via cfct after each command."
)
@Component
public class ReplayCommand implements Callable<Integer> {

    private final AregressProperties props;

    public ReplayCommand(AregressProperties props) {
        this.props = props;
    }

    /** Exactly one of --timestamp / --file is required (mutually exclusive). */
    @ArgGroup(exclusive = true, multiplicity = "1")
    private ReplayTarget replayTarget;

    static class ReplayTarget {
        @Option(names = "--timestamp",
                description = "Baseline timestamp of an already-imported batch, e.g. 2026-04-23T08-32-03.309Z")
        String timestamp;

        @Option(names = "--file",
                description = "Recording file to import into both apps (instead of --timestamp); "
                        + "each app's import endpoint returns the baseline timestamp to replay")
        Path file;
    }

    // For these, a null value means "fall back to configuration" (aregress.* / application.yml).
    @Option(names = "--app-a", description = "Base URL for app-a (overrides config aregress.app-a)")
    private String appABase;

    @Option(names = "--app-b", description = "Base URL for app-b (overrides config aregress.app-b)")
    private String appBBase;

    @Option(names = "--cfct", description = "Base URL of the cfct automation REST API (overrides config aregress.cfct)")
    private String cfctBase;

    @Option(names = "--cfct-username", description = "Basic-Auth user for the cfct automation API (overrides config aregress.cfct-username)")
    private String cfctUsername;

    @Option(names = "--username", required = true,
            description = "Username for the Causeway app login (used for both app-a and app-b)")
    private String username;

    @Option(names = "--password", required = true, interactive = true, arity = "0..1",
            description = "Password for the Causeway app login (prompted if not supplied)")
    private String password;

    @Option(names = "--cfct-password", required = true, interactive = true, arity = "0..1",
            description = "Password (Basic-Auth secret) for the cfct automation API (prompted if not supplied)")
    private String cfctPassword;

    @Option(names = "--headless",
            description = "Run browser in headless mode (default: headed)")
    private boolean headless;

    @Override
    public Integer call() {
        // CLI option wins; otherwise the configured value (or its bundled default).
        String appA = appABase != null ? appABase : props.getAppA();
        String appB = appBBase != null ? appBBase : props.getAppB();
        String cfctUrl = cfctBase != null ? cfctBase : props.getCfct();
        String cfctUser = cfctUsername != null ? cfctUsername : props.getCfctUsername();

        // Resolve each app's replay timestamp: either --timestamp, or — for --file — by importing the
        // recording into each app and using the baseline timestamp it returns.
        String timestampA;
        String timestampB;
        if (replayTarget.file != null) {
            try {
                timestampA = new AppImportClient(appA, username, password).importRecording(replayTarget.file);
                timestampB = new AppImportClient(appB, username, password).importRecording(replayTarget.file);
            } catch (AppImportClient.ImportException e) {
                System.out.println("import failed: " + e.getMessage());
                return 2;
            }
        } else {
            timestampA = replayTarget.timestamp;
            timestampB = replayTarget.timestamp;
        }
        String pathPrefix = "/wicket/entity/isis.ext.commandLog.CommandReplayManager:";

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                BrowserContext context = browser.newContext();

                CausewayReplayPage appAPage = new CausewayReplayPage(context.newPage(), username, password);
                CausewayReplayPage appBPage = new CausewayReplayPage(context.newPage(), username, password);
                CfctClient cfct = new CfctClient(cfctUrl, cfctUser, cfctPassword);

                appAPage.navigateTo(appA + pathPrefix + timestampA);
                appBPage.navigateTo(appB + pathPrefix + timestampB);

                int step = 0;
                while (appAPage.hasPendingCommands()) {
                    step++;
                    String member = appAPage.oldestCommandMember();

                    appAPage.replayNext();
                    if ("Failed".equalsIgnoreCase(appAPage.oldestReplayState())) {
                        System.out.println("[step " + step + "] " + member + " — replay FAILED on app-a");
                        return 1;
                    }
                    appBPage.replayNext();
                    if ("Failed".equalsIgnoreCase(appBPage.oldestReplayState())) {
                        System.out.println("[step " + step + "] " + member + " — replay FAILED on app-b");
                        return 1;
                    }

                    ComparisonResult result;
                    try {
                        result = cfct.latestComparison();
                    } catch (CfctClient.CfctApiException e) {
                        System.out.println("[step " + step + "] " + member + " — cfct automation error: " + e.getMessage());
                        return 2;
                    }
                    if (result.hasDifferences) {
                        System.out.println("[step " + step + "] " + member + " replayed... FAIL"
                                + " — database divergence: " + result.describeDifferences());
                        return 1;
                    }

                    String suffix = result.comparedTableCount() == 0 ? " (no footprint)" : "";
                    System.out.println("[step " + step + "] " + member + " replayed... OK" + suffix);
                }

                System.out.println("All " + step + " steps passed.");
                return 0;
            }
        }
    }
}
