package de.germanminer.addon.feature;

import de.germanminer.addon.config.AddonConfig;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses GermanMiner's {@code /damagelog} chat output and evaluates the 30-second
 * "Notwehr" (self-defense) rule for each combat interaction, while also supporting
 * a live 30-second Notwehr HUD countdown when attacked.
 *
 * <p>Observed GermanMiner Damage-Log format from server logs:
 * <pre>
 * §e§l»§7 §6♺§e Bitte warte, dein Damage-Log wird vorbereitet...
 *             »  Damage-Log von PflanzenKaktus  «
 *           §7§o(§cErlittener Schaden §7§o//§a Zugefügter Schaden§7§o)
 * §700:26:23 §c● §cHerrSieger §7» §cPflanzenKaktus §7§oDMG
 * §700:41:32 §c● §cHerrSieger §7» §cPflanzenKaktus §7§oDMG
 * </pre>
 */
public class DamageLogTracker {
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern HEADER_PATTERN = Pattern.compile(
        "»\\s*Damage-Log\\s+von\\s+([A-Za-z0-9_]{2,16})\\s*«",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
        "^\\s*(\\d{2}:\\d{2}:\\d{2})\\s*[^A-Za-z0-9_\\s]*\\s*([A-Za-z0-9_]{2,16})\\s*(?:»|\\u00BB|->|>)\\s*([A-Za-z0-9_]{2,16})(?:\\s+(.*))?$"
    );
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum Direction {
        INCOMING, // Erlittener Schaden (Someone hit you)
        OUTGOING  // Zugefügter Schaden (You hit someone)
    }

    public enum NotwehrStatus {
        INCOMING_ATTACK,      // Opponent attacked you -> opens/refreshes 30s Notwehr window
        VALID_NOTWEHR,        // You attacked/killed opponent within <= 30s of their attack
        EXPIRED_NOTWEHR,      // You attacked opponent > 30s after their last attack
        FIRST_STRIKE_BY_SELF  // You attacked opponent without any prior attack from them
    }

    public record DamageLogEntry(
        String timestamp,
        int secondsOfDay,
        String attacker,
        String victim,
        String opponent,
        Direction direction,
        String hoverDetail,
        NotwehrStatus status,
        int deltaSecondsFromIncoming,
        String notwehrValidUntilTime,
        String statusExplanation
    ) {}

    public record OpponentSummary(
        String opponentName,
        int incomingHits,
        int outgoingHits,
        boolean opponentStruckFirst,
        String firstInteractionTime,
        String lastIncomingTime,
        String lastOutgoingTime,
        String notwehrValidUntilTime,
        int lastResponseDeltaSeconds,
        int maxResponseDeltaSeconds,
        NotwehrStatus overallStatus,
        String summaryVerdict
    ) {}

    private final AddonConfig config;

    private String logOwnerName = "";
    private long lastHeaderTimestampMs = 0L;
    private long lastEntryTimestampMs = 0L;
    private boolean summaryPendingForCurrentLog = false;

    private final List<DamageLogEntry> rawSessionEntries = new ArrayList<>();
    private List<DamageLogEntry> evaluatedEntries = Collections.emptyList();
    private List<OpponentSummary> opponentSummaries = Collections.emptyList();

    // Live 30-second Notwehr countdown timer state
    private long notwehrStartMs = 0L;
    private long notwehrDurationMs = 0L;
    private String activeAttackerName = "";

    public DamageLogTracker(AddonConfig config) {
        this.config = config;
    }

    /**
     * Processes a chat line (both stripped plain text and optional raw formatted/hover text).
     *
     * @return {@code true} if the line belonged to a GermanMiner Damage-Log output.
     */
    public synchronized boolean onChatMessage(String plainMessage, String rawFormattedMessage, String hoverText, String localPlayerName) {
        if (plainMessage == null) {
            return false;
        }
        String clean = stripFormatting(plainMessage).trim();
        if (clean.isEmpty()) {
            return false;
        }

        if (clean.contains("Bitte warte, dein Damage-Log wird vorbereitet")) {
            return true;
        }

        Matcher headerMatcher = HEADER_PATTERN.matcher(clean);
        if (headerMatcher.find()) {
            this.logOwnerName = headerMatcher.group(1);
            this.lastHeaderTimestampMs = System.currentTimeMillis();
            this.lastEntryTimestampMs = this.lastHeaderTimestampMs;
            this.rawSessionEntries.clear();
            this.evaluatedEntries = Collections.emptyList();
            this.opponentSummaries = Collections.emptyList();
            this.summaryPendingForCurrentLog = true;
            return true;
        }

        if (clean.contains("Erlittener Schaden") && clean.contains("Zugef")) {
            this.lastEntryTimestampMs = System.currentTimeMillis();
            return true;
        }

        Matcher entryMatcher = ENTRY_PATTERN.matcher(clean);
        if (entryMatcher.matches()) {
            String timeStr = entryMatcher.group(1);
            String attacker = entryMatcher.group(2);
            String victim = entryMatcher.group(3);
            String tail = entryMatcher.group(4) != null ? entryMatcher.group(4).trim() : "";

            long now = System.currentTimeMillis();
            // If a new entry arrives > 5 seconds after the previous batch without a header, start a fresh batch
            if (now - lastEntryTimestampMs > 5000L && now - lastHeaderTimestampMs > 5000L) {
                this.rawSessionEntries.clear();
                this.summaryPendingForCurrentLog = true;
            }
            this.lastEntryTimestampMs = now;

            String owner = !logOwnerName.isBlank()
                ? logOwnerName
                : (localPlayerName != null && !localPlayerName.isBlank() ? localPlayerName : victim);
            if (logOwnerName.isBlank()) {
                logOwnerName = owner;
            }

            Direction dir;
            if (victim.equalsIgnoreCase(owner)) {
                dir = Direction.INCOMING;
            } else if (attacker.equalsIgnoreCase(owner)) {
                dir = Direction.OUTGOING;
            } else if (rawFormattedMessage != null && rawFormattedMessage.contains("§a")) {
                dir = Direction.OUTGOING;
            } else {
                dir = Direction.INCOMING;
            }

            String opponent = (dir == Direction.INCOMING) ? attacker : victim;
            int secOfDay = parseSecondsOfDay(timeStr);
            String detail = (hoverText != null && !hoverText.isBlank())
                ? stripFormatting(hoverText).replace('\n', ' ').trim()
                : tail;

            DamageLogEntry preliminary = new DamageLogEntry(
                timeStr,
                secOfDay,
                attacker,
                victim,
                opponent,
                dir,
                detail,
                NotwehrStatus.INCOMING_ATTACK,
                0,
                formatSecondsOfDay(secOfDay + config.notwehrWindowSeconds),
                ""
            );
            this.rawSessionEntries.add(preliminary);
            recalculateAnalysis();

            // If this incoming attack happened within the last notwehrWindowSeconds of wall-clock time, sync live timer
            if (dir == Direction.INCOMING) {
                LocalTime nowTime = LocalTime.now();
                int nowSecOfDay = nowTime.toSecondOfDay();
                int ageSeconds = computeTimeDeltaSeconds(secOfDay, nowSecOfDay);
                if (ageSeconds >= 0 && ageSeconds < config.notwehrWindowSeconds) {
                    int remainingSec = config.notwehrWindowSeconds - ageSeconds;
                    triggerNotwehrCountdown(attacker, remainingSec * 1000L);
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Convenience overload for unit testing or plain-text log parsing.
     */
    public synchronized boolean onChatMessage(String plainMessage, String localPlayerName) {
        return onChatMessage(plainMessage, plainMessage, "", localPlayerName);
    }

    /**
     * Evaluates all entries in {@code rawSessionEntries} chronologically and computes
     * both per-entry Notwehr status and per-opponent summaries.
     */
    private void recalculateAnalysis() {
        int windowSec = Math.max(5, config.notwehrWindowSeconds);
        List<DamageLogEntry> evaluated = new ArrayList<>(rawSessionEntries.size());

        // Track last incoming attack time per opponent (in seconds of day)
        Map<String, Integer> lastIncomingByOpponent = new LinkedHashMap<>();
        Map<String, Boolean> opponentStruckFirstMap = new LinkedHashMap<>();
        Map<String, Boolean> seenOpponentMap = new LinkedHashMap<>();

        for (DamageLogEntry raw : rawSessionEntries) {
            String oppKey = raw.opponent().toLowerCase();
            int t = raw.secondsOfDay();

            if (!seenOpponentMap.containsKey(oppKey)) {
                seenOpponentMap.put(oppKey, true);
                opponentStruckFirstMap.put(oppKey, raw.direction() == Direction.INCOMING);
            }

            if (raw.direction() == Direction.INCOMING) {
                lastIncomingByOpponent.put(oppKey, t);
                String validUntil = formatSecondsOfDay(t + windowSec);
                String explanation = String.format(
                    "Treffer erlitten von %s — Notwehr erlaubt bis %s (%ds)",
                    raw.attacker(), validUntil, windowSec
                );
                evaluated.add(new DamageLogEntry(
                    raw.timestamp(),
                    t,
                    raw.attacker(),
                    raw.victim(),
                    raw.opponent(),
                    Direction.INCOMING,
                    raw.hoverDetail(),
                    NotwehrStatus.INCOMING_ATTACK,
                    0,
                    validUntil,
                    explanation
                ));
            } else {
                Integer lastIn = lastIncomingByOpponent.get(oppKey);
                if (lastIn == null) {
                    String explanation = String.format(
                        "KEINE NOTWEHR — Du hast %s ohne vorherigen Gegentreffer geschlagen!",
                        raw.victim()
                    );
                    evaluated.add(new DamageLogEntry(
                        raw.timestamp(),
                        t,
                        raw.attacker(),
                        raw.victim(),
                        raw.opponent(),
                        Direction.OUTGOING,
                        raw.hoverDetail(),
                        NotwehrStatus.FIRST_STRIKE_BY_SELF,
                        -1,
                        "-",
                        explanation
                    ));
                } else {
                    int delta = computeTimeDeltaSeconds(lastIn, t);
                    String validUntil = formatSecondsOfDay(lastIn + windowSec);
                    if (delta >= 0 && delta <= windowSec) {
                        String explanation = String.format(
                            "NOTWEHR GÜLTIG (%ds nach Angriff von %s, Limit: %ds)",
                            delta, raw.victim(), windowSec
                        );
                        evaluated.add(new DamageLogEntry(
                            raw.timestamp(),
                            t,
                            raw.attacker(),
                            raw.victim(),
                            raw.opponent(),
                            Direction.OUTGOING,
                            raw.hoverDetail(),
                            NotwehrStatus.VALID_NOTWEHR,
                            delta,
                            validUntil,
                            explanation
                        ));
                    } else {
                        int over = Math.max(1, delta - windowSec);
                        String explanation = String.format(
                            "NOTWEHR ABGELAUFEN (%ds nach Angriff von %s — +%ds über %ds-Limit!)",
                            delta, raw.victim(), over, windowSec
                        );
                        evaluated.add(new DamageLogEntry(
                            raw.timestamp(),
                            t,
                            raw.attacker(),
                            raw.victim(),
                            raw.opponent(),
                            Direction.OUTGOING,
                            raw.hoverDetail(),
                            NotwehrStatus.EXPIRED_NOTWEHR,
                            delta,
                            validUntil,
                            explanation
                        ));
                    }
                }
            }
        }

        this.evaluatedEntries = Collections.unmodifiableList(evaluated);

        // Build per-opponent summaries
        Map<String, List<DamageLogEntry>> byOpponent = new LinkedHashMap<>();
        for (DamageLogEntry e : evaluated) {
            byOpponent.computeIfAbsent(e.opponent(), k -> new ArrayList<>()).add(e);
        }

        List<OpponentSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<DamageLogEntry>> entry : byOpponent.entrySet()) {
            String opp = entry.getKey();
            List<DamageLogEntry> list = entry.getValue();
            int inCount = 0;
            int outCount = 0;
            String firstTime = list.get(0).timestamp();
            String lastInTime = "-";
            String lastOutTime = "-";
            String validUntil = "-";
            int lastDelta = -1;
            int maxDelta = -1;
            boolean anyFirstStrikeSelf = false;
            boolean anyExpired = false;

            for (DamageLogEntry e : list) {
                if (e.direction() == Direction.INCOMING) {
                    inCount++;
                    lastInTime = e.timestamp();
                    validUntil = e.notwehrValidUntilTime();
                } else {
                    outCount++;
                    lastOutTime = e.timestamp();
                    if (e.deltaSecondsFromIncoming() >= 0) {
                        lastDelta = e.deltaSecondsFromIncoming();
                        maxDelta = Math.max(maxDelta, e.deltaSecondsFromIncoming());
                    }
                    if (e.status() == NotwehrStatus.FIRST_STRIKE_BY_SELF) {
                        anyFirstStrikeSelf = true;
                    } else if (e.status() == NotwehrStatus.EXPIRED_NOTWEHR) {
                        anyExpired = true;
                    }
                }
            }

            boolean oppFirst = opponentStruckFirstMap.getOrDefault(opp.toLowerCase(), inCount > 0);
            NotwehrStatus overall;
            String verdict;

            if (outCount == 0) {
                overall = NotwehrStatus.INCOMING_ATTACK;
                verdict = String.format(
                    "Angegriffen von %s (%dx erlitten, keine Gegenwehr) — Notwehr galt bis %s",
                    opp, inCount, validUntil
                );
            } else if (anyFirstStrikeSelf) {
                overall = NotwehrStatus.FIRST_STRIKE_BY_SELF;
                verdict = String.format(
                    "KEINE NOTWEHR gegen %s — Du hast zuerst geschlagen (%dx zugefügt, %dx erlitten)",
                    opp, outCount, inCount
                );
            } else if (anyExpired) {
                overall = NotwehrStatus.EXPIRED_NOTWEHR;
                verdict = String.format(
                    "NOTWEHR ÜBERSCHRITTEN gegen %s — Max. Abstand %ds (> %ds Limit!)",
                    opp, maxDelta, windowSec
                );
            } else {
                overall = NotwehrStatus.VALID_NOTWEHR;
                verdict = String.format(
                    "NOTWEHR LEGITIM gegen %s — Gegenwehr innerhalb von %ds (<= %ds Limit)",
                    opp, maxDelta, windowSec
                );
            }

            summaries.add(new OpponentSummary(
                opp,
                inCount,
                outCount,
                oppFirst,
                firstTime,
                lastInTime,
                lastOutTime,
                validUntil,
                lastDelta,
                maxDelta,
                overall,
                verdict
            ));
        }

        this.opponentSummaries = Collections.unmodifiableList(summaries);
    }

    /**
     * Checks if a newly parsed Damage-Log batch has finished arriving (e.g. 250ms after the last line)
     * so the client can print a clean summary once per {@code /damagelog} invocation.
     */
    public synchronized boolean consumePendingLogSummaryIfReady() {
        if (!summaryPendingForCurrentLog || evaluatedEntries.isEmpty()) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - lastEntryTimestampMs;
        if (elapsed >= 250L) {
            summaryPendingForCurrentLog = false;
            return true;
        }
        return false;
    }

    /**
     * Triggers the live 30-second Notwehr HUD countdown when a player hits you in-game.
     */
    public synchronized void triggerLiveIncomingHit(String attackerName) {
        triggerNotwehrCountdown(
            (attackerName != null && !attackerName.isBlank()) ? attackerName : "Spieler",
            Math.max(5, config.notwehrWindowSeconds) * 1000L
        );
    }

    public synchronized void triggerNotwehrCountdown(String attackerName, long durationMillis) {
        if (durationMillis <= 0L) {
            return;
        }
        this.activeAttackerName = (attackerName != null && !attackerName.isBlank()) ? attackerName : "Unbekannt";
        this.notwehrStartMs = System.currentTimeMillis();
        this.notwehrDurationMs = durationMillis;
    }

    public synchronized void cancelNotwehrCountdown() {
        this.notwehrDurationMs = 0L;
    }

    public synchronized boolean isNotwehrTimerActive() {
        return getRemainingNotwehrMillis() > 0L;
    }

    public synchronized long getRemainingNotwehrMillis() {
        if (notwehrDurationMs <= 0L) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - notwehrStartMs;
        return Math.max(0L, notwehrDurationMs - elapsed);
    }

    public synchronized double getRemainingNotwehrSeconds() {
        return getRemainingNotwehrMillis() / 1000.0;
    }

    public synchronized float getNotwehrProgressRatio() {
        if (notwehrDurationMs <= 0L) {
            return 0.0f;
        }
        return Math.clamp((float) getRemainingNotwehrMillis() / (float) notwehrDurationMs, 0.0f, 1.0f);
    }

    public synchronized String getActiveAttackerName() {
        return activeAttackerName;
    }

    public synchronized String getLogOwnerName() {
        return logOwnerName;
    }

    public synchronized List<DamageLogEntry> getEvaluatedEntries() {
        return evaluatedEntries;
    }

    public synchronized List<OpponentSummary> getOpponentSummaries() {
        return opponentSummaries;
    }

    public static int parseSecondsOfDay(String hhMmSs) {
        try {
            String[] parts = hhMmSs.trim().split(":");
            if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = Integer.parseInt(parts[2]);
                return h * 3600 + m * 60 + s;
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    public static String formatSecondsOfDay(int totalSeconds) {
        int wrapped = ((totalSeconds % 86400) + 86400) % 86400;
        return LocalTime.ofSecondOfDay(wrapped).format(TIME_FMT);
    }

    /**
     * Computes {@code endSec - startSec} handling midnight rollover (`23:59:50` -> `00:00:10`).
     */
    public static int computeTimeDeltaSeconds(int startSec, int endSec) {
        int diff = endSec - startSec;
        if (diff < -43200) {
            diff += 86400; // rolled over midnight
        }
        return diff;
    }

    public static String stripFormatting(String input) {
        if (input == null) {
            return "";
        }
        return COLOR_STRIP_PATTERN.matcher(input).replaceAll("");
    }
}
