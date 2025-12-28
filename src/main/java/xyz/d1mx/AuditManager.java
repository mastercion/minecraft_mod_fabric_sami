package xyz.d1mx;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuditManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SMI-Audit");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_FILE = "smi_audit.txt";

    public static void logView(ServerPlayer admin, String targetName) {
        String entry = String.format("[%s] VIEW: %s checked inventory of %s",
                getTime(), admin.getScoreboardName(), targetName);
        writeLog(admin.getServer(), entry);
    }

    public static void logItemRestore(ServerPlayer admin, String targetName, ItemStack item) {
        String entry = String.format("[%s] RESTORE ITEM: %s gave %s to %s",
                getTime(), admin.getScoreboardName(), item.getHoverName().getString(), targetName);
        writeLog(admin.getServer(), entry);
    }

    public static void logFullRestore(ServerPlayer admin, String targetName) {
        String entry = String.format("[%s] RESTORE ALL: %s overwrote inventory of %s",
                getTime(), admin.getScoreboardName(), targetName);
        writeLog(admin.getServer(), entry);
    }

    private static String getTime() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    private static void writeLog(MinecraftServer server, String message) {
        if (server == null) return;

        Path path = server.getWorldPath(LevelResource.ROOT).resolve(LOG_FILE);
        try {
            // Append line safely
            Files.writeString(path, message + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to write to audit log", e);
        }
    }

    public static List<String> getRecentLogs(MinecraftServer server, int limit) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(LOG_FILE);
        if (!Files.exists(path)) return Collections.emptyList();

        try {
            List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Collections.reverse(allLines); // Show newest events first

            // Return a safe sublist view
            return allLines.subList(0, Math.min(allLines.size(), limit));
        } catch (IOException e) {
            LOGGER.error("Failed to read audit log", e);
            return new ArrayList<>();
        }
    }
}