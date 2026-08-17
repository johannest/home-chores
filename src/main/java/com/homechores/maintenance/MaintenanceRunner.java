package com.homechores.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.homechores.domain.CompletionRepository;
import com.homechores.domain.Home;
import com.homechores.domain.HomeRepository;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.service.BackupService;
import com.homechores.service.ChoreService;
import com.homechores.service.HomeCleanupService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Offline operator commands, driven by {@code tools/flashchores-admin.py}.
 *
 * <p>This exists so that servicing a GDPR erasure request doesn't need an authenticated
 * admin screen on the public internet, and doesn't need hand-written SQL either: deletion
 * goes through {@link ChoreService#deleteHome} so the cascade across every table stays in
 * one tested place.
 *
 * <p>Activated only when {@code --maintenance.command=…} is passed, in which case the app
 * runs the command, prints a JSON result and shuts itself down. Vaadin's Spring
 * integration requires a web context, so the launcher gives it an ephemeral loopback port
 * that lives for the couple of seconds the command takes; nothing reachable is exposed.
 * H2 locks its file exclusively, so the service must be stopped first.
 *
 * <p>The JSON is fenced between markers so the caller can find it in amongst Spring's own
 * logging on stdout.
 */
@Component
@ConditionalOnProperty(name = "maintenance.command")
public class MaintenanceRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String JSON_BEGIN = "---FLASHCHORES-JSON-BEGIN---";
    public static final String JSON_END = "---FLASHCHORES-JSON-END---";

    private final HomeRepository homes;
    private final MemberRepository members;
    private final CompletionRepository completions;
    private final ChoreService choreService;
    private final BackupService backupService;
    private final HomeCleanupService cleanup;
    private final ApplicationContext context;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private int exitCode;

    public MaintenanceRunner(HomeRepository homes, MemberRepository members,
                            CompletionRepository completions, ChoreService choreService,
                            BackupService backupService, HomeCleanupService cleanup,
                            ApplicationContext context) {
        this.homes = homes;
        this.members = members;
        this.completions = completions;
        this.choreService = choreService;
        this.backupService = backupService;
        this.cleanup = cleanup;
        this.context = context;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> result = new LinkedHashMap<>();
        String command = value(args, "maintenance.command", "");
        try {
            result.put("command", command);
            switch (command) {
                case "list" -> result.put("homes", listHomes());
                case "show" -> result.putAll(show(required(args, "maintenance.code")));
                case "export" -> result.putAll(export(required(args, "maintenance.code"),
                        required(args, "maintenance.out")));
                case "delete" -> result.putAll(delete(required(args, "maintenance.code")));
                case "purge" -> result.putAll(purge(
                        Integer.parseInt(required(args, "maintenance.days")),
                        Boolean.parseBoolean(value(args, "maintenance.dry-run", "false"))));
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            }
            result.put("ok", true);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            exitCode = 1;
        }
        emit(result);
        // Vaadin's Spring integration needs a web context, so this run has a (loopback,
        // ephemeral) server holding the JVM open. Close down cleanly rather than
        // System.exit, so the H2 file is released properly.
        System.exit(SpringApplication.exit(context, this));
    }

    // ---- Commands -----------------------------------------------------------

    private List<Map<String, Object>> listHomes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Home home : homes.findAll()) {
            out.add(summary(home));
        }
        out.sort((a, b) -> String.valueOf(a.get("lastActive")).compareTo(String.valueOf(b.get("lastActive"))));
        return out;
    }

    private Map<String, Object> show(String code) {
        Home home = homes.findById(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No home with code " + code));
        Map<String, Object> out = new LinkedHashMap<>(summary(home));
        List<Map<String, Object>> people = new ArrayList<>();
        for (Member m : members.findByHomeCodeOrderByJoinedAtAsc(home.getCode())) {
            people.add(Map.of(
                    "name", m.getName(),
                    "admin", m.isAdmin(),
                    "joinedAt", String.valueOf(m.getJoinedAt()),
                    "approvedChores", choreService.completionCount(m.getId())));
        }
        // Under a separate key: "members" stays the count in every command's output, so
        // the caller doesn't have to know which shape it's looking at.
        out.put("memberDetails", people);
        out.put("chores", choreService.tasksOf(home.getCode()).size());
        return out;
    }

    /** Writes the home's full JSON backup, so an erasure can be undone or evidenced. */
    private Map<String, Object> export(String code, String out) throws Exception {
        String normalized = code.trim().toUpperCase();
        if (homes.findById(normalized).isEmpty()) {
            throw new IllegalArgumentException("No home with code " + normalized);
        }
        Path path = Path.of(out);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        String json = backupService.export(normalized);
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return Map.of("code", normalized, "exportedTo", path.toAbsolutePath().toString(),
                "bytes", json.getBytes(StandardCharsets.UTF_8).length);
    }

    private Map<String, Object> delete(String code) {
        String normalized = code.trim().toUpperCase();
        Home home = homes.findById(normalized)
                .orElseThrow(() -> new IllegalArgumentException("No home with code " + normalized));
        String name = home.getName();
        boolean deleted = choreService.deleteHome(normalized);
        return Map.of("code", normalized, "name", name, "deleted", deleted);
    }

    /**
     * Applies the abandoned-home rule with an explicit window, independent of whatever the
     * scheduled sweep is configured to do (so it works on a server with retention off).
     */
    private Map<String, Object> purge(int days, boolean dryRun) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        if (dryRun) {
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (Home home : cleanup.findAbandoned(cutoff)) {
                candidates.add(summary(home));
            }
            return Map.of("days", days, "dryRun", true, "candidates", candidates);
        }
        return Map.of("days", days, "dryRun", false,
                "purged", cleanup.purgeAbandonedHomes(cutoff));
    }

    // ---- Helpers ------------------------------------------------------------

    private Map<String, Object> summary(Home home) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", home.getCode());
        m.put("name", home.getName());
        m.put("members", members.countByHomeCode(home.getCode()));
        m.put("hasHistory", completions.existsByHomeCode(home.getCode()));
        m.put("created", String.valueOf(home.getCreatedAt()));
        m.put("lastActive", String.valueOf(home.lastActiveOrCreated()));
        m.put("lastActiveRecorded", home.getLastActiveAt() != null);
        return m;
    }

    private void emit(Map<String, Object> result) {
        try {
            System.out.println(JSON_BEGIN);
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            System.out.println(JSON_END);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize maintenance result", e);
        }
    }

    private String value(ApplicationArguments args, String name, String fallback) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? fallback : values.get(0);
    }

    private String required(ApplicationArguments args, String name) {
        String v = value(args, name, "");
        if (v.isBlank()) {
            throw new IllegalArgumentException("Missing required argument --" + name);
        }
        return v;
    }
}
