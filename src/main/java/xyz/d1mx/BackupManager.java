package xyz.d1mx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import xyz.d1mx.SaveMyItems; // Import main class for logging

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class BackupManager {
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void createBackup(ServerPlayer player) {
        if (player == null) return;

        ListTag inventoryTag = new ListTag();
        player.getInventory().save(inventoryTag);

        CompoundTag root = new CompoundTag();
        root.put("Inventory", inventoryTag);
        root.putLong("Timestamp", System.currentTimeMillis());

        try {
            Path saveDir = getPlayerBackupDir(player);
            String fileName = LocalDateTime.now().format(FILE_NAME_FORMAT) + ".nbt";
            File file = saveDir.resolve(fileName).toFile();

            NbtIo.writeCompressed(root, file.toPath());
        } catch (IOException e) {
            System.err.println("Failed to save backup for " + player.getName().getString());
            e.printStackTrace();
        }
    }

    // Cleanup Logic
    public static void cleanupOldBackups(MinecraftServer server, int retentionDays) {
        Path rootPath = server.getWorldPath(LevelResource.ROOT).resolve("smi_backups");
        if (!Files.exists(rootPath)) return;

        Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        final int[] deletedCount = {0};

        try (Stream<Path> playerFolders = Files.walk(rootPath, 1)) {
            playerFolders.filter(Files::isDirectory).forEach(folder -> {
                // Ignore the root folder itself
                if (folder.equals(rootPath)) return;

                File[] files = folder.toFile().listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (isFileOlderThan(file, threshold)) {
                            if (file.delete()) {
                                deletedCount[0]++;
                            }
                        }
                    }
                }
            });
        } catch (IOException e) {
            SaveMyItems.LOGGER.error("Error during backup cleanup", e);
        }

        if (deletedCount[0] > 0) {
            SaveMyItems.LOGGER.info("[SMI] Cleanup: Removed {} old backups (older than {} days).", deletedCount[0], retentionDays);
        }
    }

    private static boolean isFileOlderThan(File file, Instant threshold) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            return attrs.lastModifiedTime().toInstant().isBefore(threshold);
        } catch (IOException e) {
            return false;
        }
    }

    public static List<ItemStack> loadBackup(MinecraftServer server, String uuid, String fileName) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("smi_backups")
                .resolve(uuid)
                .resolve(fileName);

        if (!path.toFile().exists()) return Collections.emptyList();

        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            ListTag list = root.getList("Inventory", Tag.TAG_COMPOUND);

            List<ItemStack> items = new ArrayList<>(Collections.nCopies(41, ItemStack.EMPTY));

            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int rawSlot = itemTag.getByte("Slot") & 255;

                ItemStack stack = ItemStack.parseOptional(server.registryAccess(), itemTag);

                if (!stack.isEmpty()) {
                    int guiSlot = mapRawSlotToGui(rawSlot);
                    if (guiSlot != -1) {
                        items.set(guiSlot, stack);
                    }
                }
            }
            return items;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static int mapRawSlotToGui(int rawSlot) {
        if (rawSlot >= 0 && rawSlot < 36) return rawSlot;
        if (rawSlot >= 100 && rawSlot < 104) return 36 + (rawSlot - 100);
        if (rawSlot == 150) return 40;
        return -1;
    }

    public static Path getPlayerBackupDir(ServerPlayer player) {
        Path dir = player.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("smi_backups")
                .resolve(player.getStringUUID());

        if (!dir.toFile().exists()) {
            dir.toFile().mkdirs();
        }
        return dir;
    }
}