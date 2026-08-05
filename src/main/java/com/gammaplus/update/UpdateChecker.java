package com.gammaplus.update;

import com.gammaplus.GammaMod;
import com.gammaplus.config.GammaModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UpdateChecker — asks Modrinth once per session whether a newer build exists.
 *
 * <p>Design constraints, in order of importance:
 *
 * <ul>
 *   <li><b>Never affects gameplay.</b> Every failure path — no network, DNS failure, the project
 *       not published yet (404), a rate limit, malformed JSON, an unparsable version — ends in
 *       "say nothing". A cosmetic notice is not worth a log full of stack traces, let alone a
 *       crash.</li>
 *   <li><b>Never blocks a frame.</b> The request runs on its own short-lived daemon thread with
 *       explicit timeouts, so a hung endpoint cannot stall the client or delay shutdown.</li>
 *   <li><b>Sends nothing about the player.</b> The request carries only the User-Agent Modrinth
 *       asks for, which identifies the mod and its version. No player, world or server data.</li>
 *   <li><b>Asks once.</b> One request per game session, not per world join.</li>
 * </ul>
 */
public final class UpdateChecker {

    /**
     * The Modrinth project slug — the last path segment of the project URL.
     * <b>Must match the real slug once the project is published</b>, or the check quietly 404s
     * and no notice ever appears.
     */
    private static final String MODRINTH_SLUG = "gammaplus";

    private static final String PROJECT_URL = "https://modrinth.com/mod/" + MODRINTH_SLUG;

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Gson GSON = new Gson();

    /** Newest version found upstream, or {@code null} while unknown or already up to date. */
    private static volatile String newerVersion = null;

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static final AtomicBoolean announced = new AtomicBoolean(false);
    private static volatile boolean finished = false;

    private UpdateChecker() {}

    /** The version currently running, straight from the mod metadata. */
    public static Version currentVersion() {
        return FabricLoader.getInstance().getModContainer(GammaMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion())
                .orElse(null);
    }

    /** True once a newer version has been found and not yet shown to the player. */
    public static boolean hasPendingNotice() {
        return newerVersion != null && !announced.get();
    }

    /**
     * True once the check has run to completion, whatever the outcome — including when it was
     * never started because the setting is off. Lets callers stop waiting for an answer that is
     * never coming.
     */
    public static boolean isFinished() {
        return finished || !GammaModConfig.isUpdateCheckEnabled();
    }

    /** The newer version string, or {@code null} if there isn't one. */
    public static String getNewerVersion() {
        return newerVersion;
    }

    public static String getProjectUrl() {
        return PROJECT_URL;
    }

    /** Marks the notice as shown so it appears at most once per session. */
    public static boolean markAnnounced() {
        return announced.compareAndSet(false, true);
    }

    /**
     * Kicks off the check on a background thread. Safe to call more than once; only the first
     * call does anything.
     */
    public static void startAsync() {
        if (!GammaModConfig.isUpdateCheckEnabled()) return;
        if (!started.compareAndSet(false, true)) return;

        Thread thread = new Thread(UpdateChecker::run, "GammaPlus-update-check");
        thread.setDaemon(true); // Must never hold up JVM shutdown.
        thread.start();
    }

    private static void run() {
        try {
            Version current = currentVersion();
            if (current == null) return;

            String body = fetch(current);
            if (body == null) return;

            String latest = highestVersion(body);
            if (latest == null) return;

            if (isNewerThan(latest, current)) {
                newerVersion = latest;
                GammaMod.LOGGER.info("[Gamma Plus] Update available: {} (running {})",
                        latest, current.getFriendlyString());
            }
        } catch (Throwable t) {
            // Deliberately swallowed: an update check is cosmetic and must never surface as an
            // error to the player. Logged at debug so it is still diagnosable on request.
            GammaMod.LOGGER.debug("[Gamma Plus] Update check failed.", t);
        } finally {
            finished = true;
        }
    }

    /** Returns the response body, or {@code null} on any non-200 or transport failure. */
    private static String fetch(Version current) throws Exception {
        // Ask only for builds matching this Minecraft version and loader, so a player on 26.2 is
        // never told about a release that does not run for them.
        String gameVersion = FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
        if (gameVersion == null) return null;

        String url = "https://api.modrinth.com/v2/project/" + MODRINTH_SLUG + "/version"
                + "?loaders=" + encode("[\"fabric\"]")
                + "&game_versions=" + encode("[\"" + gameVersion + "\"]");

        // Modrinth asks API consumers to identify themselves; an anonymous agent risks being
        // throttled or blocked outright.
        String userAgent = "Jom3a-J/Gamma-Plus/" + current.getFriendlyString()
                + " (github.com/Jom3a-J/Gamma-Plus-)";

        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // 404 is the normal answer before the project is published — not worth a warning.
            return response.statusCode() == 200 ? response.body() : null;
        }
    }

    /**
     * Highest {@code version_number} in the response.
     *
     * <p>Modrinth does not guarantee ordering, so every entry is compared rather than trusting
     * the first.
     */
    private static String highestVersion(String body) {
        JsonElement root = GSON.fromJson(body, JsonElement.class);
        if (root == null || !root.isJsonArray()) return null;

        JsonArray versions = root.getAsJsonArray();
        String best = null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            JsonElement number = object.get("version_number");
            if (number == null || !number.isJsonPrimitive()) continue;

            String candidate = number.getAsString();
            if (best == null || compare(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isNewerThan(String candidate, Version current) {
        try {
            return SemanticVersion.parse(stripPrefix(candidate)).compareTo((Version) current) > 0;
        } catch (Throwable t) {
            return false; // Unparsable upstream version — assume nothing.
        }
    }

    private static int compare(String a, String b) {
        try {
            return SemanticVersion.parse(stripPrefix(a)).compareTo((Version) SemanticVersion.parse(stripPrefix(b)));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Tolerates release names written as {@code v1.2.3} as well as {@code 1.2.3}. */
    private static String stripPrefix(String version) {
        String trimmed = version.trim();
        return (trimmed.startsWith("v") || trimmed.startsWith("V")) ? trimmed.substring(1) : trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
