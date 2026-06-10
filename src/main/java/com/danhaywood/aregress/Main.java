package com.danhaywood.aregress;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
        name = "aregress",
        mixinStandardHelpOptions = true,
        description = "Automates regression testing by replaying commands on two Causeway app instances and comparing their databases via cfct."
)
public class Main implements Callable<Integer> {

    @Option(names = "--timestamp", required = true,
            description = "Baseline timestamp for the CommandReplayManager URL, e.g. 2026-04-23T08-32-03.309Z")
    private String timestamp;

    @Option(names = "--app-a", defaultValue = "http://localhost:8080",
            description = "Base URL for app-a (default: ${DEFAULT-VALUE})")
    private String appABase;

    @Option(names = "--app-b", defaultValue = "http://localhost:9090",
            description = "Base URL for app-b (default: ${DEFAULT-VALUE})")
    private String appBBase;

    @Option(names = "--cfct", defaultValue = "http://localhost:10010",
            description = "Base URL for cfct (default: ${DEFAULT-VALUE})")
    private String cfctBase;

    @Option(names = "--headless",
            description = "Run browser in headless mode (default: headed)")
    private boolean headless;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public Integer call() {
        String replayPath = "/wicket/entity/isis.ext.commandLog.CommandReplayManager:" + timestamp;

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(headless);
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                BrowserContext context = browser.newContext();

                CausewayReplayPage appA = new CausewayReplayPage(context.newPage());
                CausewayReplayPage appB = new CausewayReplayPage(context.newPage());
                CfctPage cfct = new CfctPage(context.newPage());

                appA.navigateTo(appABase + replayPath);
                appB.navigateTo(appBBase + replayPath);
                cfct.navigateTo(cfctBase);

                int step = 0;
                while (appA.isReplayNextEnabled()) {
                    step++;
                    appA.clickReplayNext();
                    appB.clickReplayNext();
                    cfct.clickRefresh();
                    if (cfct.hasDifferenceTabs()) {
                        System.out.println("[step " + step + "] replayed... FAIL");
                        return 1;
                    }
                    System.out.println("[step " + step + "] replayed... OK");
                }
                System.out.println("All " + step + " steps passed.");
                return 0;
            }
        }
    }
}
