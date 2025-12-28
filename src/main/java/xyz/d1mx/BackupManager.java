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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackupManager {
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void createBackup(ServerPlayer player) {
        if (player == null) return;

        // Save raw inventory data to NBT
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

    public static List<ItemStack> loadBackup(MinecraftServer server, String uuid, String fileName) {
        Path path = server.getWorldPath(LevelResource.ROOT)
                .resolve("smi_backups")
                .resolve(uuid)
                .resolve(fileName);

        if (!path.toFile().exists()) return Collections.emptyList();

        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            ListTag list = root.getList("Inventory", Tag.TAG_COMPOUND);

            // Initialize empty list for Main(36) + Armor(4) + Offhand(1)
            List<ItemStack> items = new ArrayList<>(Collections.nCopies(41, ItemStack.EMPTY));

            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int rawSlot = itemTag.getByte("Slot") & 255;

                // 1.21 requires server registry access to correctly parse item components
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
        if (rawSlot >= 0 && rawSlot < 36) return rawSlot;      // Main inventory
        if (rawSlot >= 100 && rawSlot < 104) return 36 + (rawSlot - 100); // Armor
        if (rawSlot == 150) return 40;                        // Offhand
        return -1; // Unknown/Invalid slot
    }

    public static Path getPlayerBackupDir(ServerPlayer player) {
        Path dir = player.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("smi_backups")
                .resolve(player.getStringUUID());

        // Ensure directory exists before returning
        if (!dir.toFile().exists()) {
            dir.toFile().mkdirs();
        }
        return dir;
    }
}