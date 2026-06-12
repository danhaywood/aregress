package com.danhaywood.aregress;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "aregress",
        mixinStandardHelpOptions = true,
        description = "Automates regression testing by replaying commands on two Causeway app instances "
                + "in lockstep and comparing their databases via cfct after each command."
)
public class Main implements Callable<Integer> {

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

    @Option(names = "--app-a", defaultValue = "http://localhost:8080",
            description = "Base URL for app-a (default: ${DEFAULT-VALUE})")
    private String appABase;

    @Option(names = "--app-b", defaultValue = "http://localhost:9090",
            description = "Base URL for app-b (default: ${DEFAULT-VALUE})")
    private String appBBase;

    @Option(names = "--cfct", defaultValue = "http://localhost:10010",
            description = "Base URL of the cfct automation REST API (default: ${DEFAULT-VALUE})")
    private String cfctBase;

    @Option(names = "--username", required = true,
            description = "Username for the Causeway app login (used for both app-a and app-b)")
    private String username;

    @Option(names = "--password", required = true, interactive = true, arity = "0..1",
            description = "Password for the Causeway app login (prompted if not supplied)")
    private String password;

    @Option(names = "--cfct-username", defaultValue = "robot",
            description = "Username for HTTP Basic Auth against the cfct automation API (default: ${DEFAULT-VALUE})")
    private String cfctUsername;

    @Option(names = "--cfct-password", required = true, interactive = true, arity = "0..1",
            description = "Password (Basic-Auth secret) for the cfct automation API (prompted if not supplied)")
    private String cfctPassword;

    @Option(names = "--headless",
            description = "Run browser in headless mode (default: headed)")
    private boolean headless;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public Integer call() {
        // Resolve each app's replay timestamp: either the supplied --timestamp, or — for --file —
        // by importing the recording into each app and using the baseline timestamp it returns.
        String timestampA;
        String timestampB;
        if (replayTarget.file != null) {
            try {
                timestampA = new AppImportClient(appABase, username, password).importRecording(replayTarget.file);
                timestampB = new AppImportClient(appBBase, username, password).importRecording(replayTarget.file);
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

                CausewayReplayPage appA = new CausewayReplayPage(context.newPage(), username, password);
                CausewayReplayPage appB = new CausewayReplayPage(context.newPage(), username, password);
                CfctClient cfct = new CfctClient(cfctBase, cfctUsername, cfctPassword);

                appA.navigateTo(appABase + pathPrefix + timestampA);
                appB.navigateTo(appBBase + pathPrefix + timestampB);

                int step = 0;
                while (appA.hasPendingCommands()) {
                    step++;
                    String member = appA.oldestCommandMember();

                    appA.replayNext();
                    if ("Failed".equalsIgnoreCase(appA.oldestReplayState())) {
                        System.out.println("[step " + step + "] " + member + " — replay FAILED on app-a");
                        return 1;
                    }
                    appB.replayNext();
                    if ("Failed".equalsIgnoreCase(appB.oldestReplayState())) {
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
