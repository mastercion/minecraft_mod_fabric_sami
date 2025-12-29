package xyz.d1mx;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class SmiGui {

    public static void openMainMenu(ServerPlayer admin) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, admin, false);
        gui.setTitle(Component.literal("SaveMyItems - Menu"));

        gui.setSlot(11, new GuiElementBuilder(Items.CHEST)
                .setName(Component.literal("View Player Backups"))
                .setCallback(() -> openPlayerList(admin))
        );

        gui.setSlot(13, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .setName(Component.literal("Audit Log").withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal("See who accessed what.").withStyle(ChatFormatting.GRAY))
                .setCallback(() -> openAuditLog(admin))
        );

        gui.setSlot(15, new GuiElementBuilder(Items.CLOCK)
                .setName(Component.literal("Configure Settings"))
                .addLoreLine(Component.literal("Interval & Cleanup").withStyle(ChatFormatting.GRAY))
                .setCallback(() -> openConfigGui(admin))
        );

        gui.open();
    }

    private static void openConfigGui(ServerPlayer admin) {
        // Display 6 rows
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, admin, false);
        gui.setTitle(Component.literal("Configure Settings"));

        // GUI Backup Interval
        gui.setSlot(10, new GuiElementBuilder(Items.CLOCK)
                .setName(Component.literal("Backup Interval").withStyle(ChatFormatting.GOLD))
        );

        gui.setSlot(12, new GuiElementBuilder(Items.RED_CONCRETE)
                .setName(Component.literal("-1 Minute"))
                .setCallback(() -> {
                    if (SaveMyItems.BACKUP_INTERVAL_MINUTES > 1) {
                        SaveMyItems.BACKUP_INTERVAL_MINUTES--;
                        openConfigGui(admin);
                    }
                }));

        gui.setSlot(13, new GuiElementBuilder(Items.PAPER)
                .setName(Component.literal(SaveMyItems.BACKUP_INTERVAL_MINUTES + " Minutes"))
                .setCount(Math.min(64, Math.max(1, SaveMyItems.BACKUP_INTERVAL_MINUTES)))
        );

        gui.setSlot(14, new GuiElementBuilder(Items.GREEN_CONCRETE)
                .setName(Component.literal("+1 Minute"))
                .setCallback(() -> {
                    SaveMyItems.BACKUP_INTERVAL_MINUTES++;
                    openConfigGui(admin);
                }));

        // Cleanup
        gui.setSlot(28, new GuiElementBuilder(Items.HOPPER)
                .setName(Component.literal("Cleanup Old Backups").withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal("Deletes files older than X days").withStyle(ChatFormatting.GRAY))
        );

        gui.setSlot(30, new GuiElementBuilder(Items.RED_STAINED_GLASS)
                .setName(Component.literal("-1 Day"))
                .setCallback(() -> {
                    if (SaveMyItems.BACKUP_RETENTION_DAYS > 0) {
                        SaveMyItems.BACKUP_RETENTION_DAYS--;
                        openConfigGui(admin);
                    }
                }));

        String retentionText = (SaveMyItems.BACKUP_RETENTION_DAYS == 0) ? "Disabled (Keep Forever)" : SaveMyItems.BACKUP_RETENTION_DAYS + " Days";

        gui.setSlot(31, new GuiElementBuilder(Items.FILLED_MAP)
                .setName(Component.literal(retentionText))
                .setCount(Math.max(1, SaveMyItems.BACKUP_RETENTION_DAYS))
        );

        gui.setSlot(32, new GuiElementBuilder(Items.GREEN_STAINED_GLASS)
                .setName(Component.literal("+1 Day"))
                .setCallback(() -> {
                    SaveMyItems.BACKUP_RETENTION_DAYS++;
                    openConfigGui(admin);
                }));


        // Component Back Button
        gui.setSlot(53, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("Back"))
                .setCallback(() -> openMainMenu(admin)));
        gui.open();
    }

    private static void openAuditLog(ServerPlayer admin) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, admin, false);
        gui.setTitle(Component.literal("Audit Log (Latest Events)"));

        List<String> logs = AuditManager.getRecentLogs(admin.getServer(), 53);

        for (int i = 0; i < logs.size(); i++) {
            String logEntry = logs.get(i);

            ChatFormatting color = ChatFormatting.WHITE;
            if (logEntry.contains("RESTORE ALL")) color = ChatFormatting.RED;
            else if (logEntry.contains("RESTORE ITEM")) color = ChatFormatting.YELLOW;
            else if (logEntry.contains("VIEW")) color = ChatFormatting.AQUA;

            String timestamp = logEntry.startsWith("[") && logEntry.contains("]")
                    ? logEntry.substring(0, logEntry.indexOf("]") + 1)
                    : "";

            String action = logEntry.length() > timestamp.length()
                    ? logEntry.substring(timestamp.length()).trim()
                    : logEntry;

            gui.setSlot(i, new GuiElementBuilder(Items.PAPER)
                    .setName(Component.literal(action).withStyle(color))
                    .addLoreLine(Component.literal(timestamp).withStyle(ChatFormatting.DARK_GRAY))
            );
        }

        gui.setSlot(53, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("Back"))
                .setCallback(() -> openMainMenu(admin))
        );
        gui.open();
    }

    private static void openPlayerList(ServerPlayer admin) {
        Path backupRoot = admin.getServer().getWorldPath(LevelResource.ROOT).resolve("smi_backups");
        File[] playerFolders = backupRoot.toFile().listFiles(File::isDirectory);

        if (playerFolders == null || playerFolders.length == 0) {
            admin.sendSystemMessage(Component.literal("No backups found yet."));
            return;
        }

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, admin, false);
        gui.setTitle(Component.literal("Select Player"));

        int slot = 0;
        for (File folder : playerFolders) {
            if (slot >= 53) break;

            String uuidStr = folder.getName();
            String name = resolveName(admin.getServer(), uuidStr);

            gui.setSlot(slot++, new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setName(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                    .addLoreLine(Component.literal(uuidStr).withStyle(ChatFormatting.DARK_GRAY))
                    .setCallback(() -> openBackupList(admin, uuidStr, name))
            );
        }

        gui.setSlot(53, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("Back"))
                .setCallback(() -> openMainMenu(admin)));
        gui.open();
    }

    private static void openBackupList(ServerPlayer admin, String targetUuid, String targetName) {
        Path playerDir = admin.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("smi_backups").resolve(targetUuid);

        File[] backups = playerDir.toFile().listFiles();

        if (backups == null || backups.length == 0) {
            admin.sendSystemMessage(Component.literal("This player has no backups."));
            return;
        }

        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, admin, false);
        gui.setTitle(Component.literal("Backups: " + targetName));

        int slot = 0;
        for (File backup : backups) {
            if (slot >= 53) break;

            List<ItemStack> backedUpItems = BackupManager.loadBackup(admin.getServer(), targetUuid, backup.getName());

            ItemStack shulkerStack = new ItemStack(Items.SHULKER_BOX);
            List<ItemStack> containerItems = new ArrayList<>();
            for (ItemStack item : backedUpItems) {
                if (!item.isEmpty()) {
                    containerItems.add(item.copy());
                }
                if (containerItems.size() >= 27) break;
            }

            ItemContainerContents contents = ItemContainerContents.fromItems(containerItems);
            shulkerStack.set(DataComponents.CONTAINER, contents);

            gui.setSlot(slot++, new GuiElementBuilder(shulkerStack)
                    .setName(Component.literal(backup.getName().replace(".nbt", "")))
                    .setCallback(() -> openBackupView(admin, targetUuid, targetName, backup.getName()))
            );
        }

        gui.setSlot(53, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("Back"))
                .setCallback(() -> openPlayerList(admin)));
        gui.open();
    }

    private static void openBackupView(ServerPlayer admin, String targetUuid, String targetName, String fileName) {
        AuditManager.logView(admin, targetName);

        List<ItemStack> items = BackupManager.loadBackup(admin.getServer(), targetUuid, fileName);
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, admin, false);
        gui.setTitle(Component.literal("Viewing: " + fileName.replace(".nbt", "")));

        for (int i = 0; i < items.size() && i < 45; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            gui.setSlot(i, new GuiElementBuilder(stack.copy())
                    .addLoreLine(Component.literal(""))
                    .addLoreLine(Component.literal("Left Click: Give to YOU").withStyle(ChatFormatting.GREEN))
                    .addLoreLine(Component.literal("Right Click: Give to TARGET").withStyle(ChatFormatting.RED))
                    .setCallback((index, type, action) -> {
                        if (type.isLeft) {
                            admin.getInventory().add(stack.copy());
                        } else if (type.isRight) {
                            attemptRestoreItem(admin, targetUuid, targetName, stack.copy());
                        }
                    })
            );
        }

        gui.setSlot(45, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("Back"))
                .setCallback(() -> openBackupList(admin, targetUuid, targetName))
        );

        gui.setSlot(53, new GuiElementBuilder(Items.NETHER_STAR)
                .setName(Component.literal("RESTORE FULL INVENTORY"))
                .addLoreLine(Component.literal("Overwrite target's inventory"))
                .setCallback(() -> {
                    restoreFullInventory(admin, targetUuid, targetName, items);
                    admin.sendSystemMessage(Component.literal("Inventory restored!"));
                    gui.close();
                })
        );

        gui.open();
    }

    private static void attemptRestoreItem(ServerPlayer admin, String targetUuid, String targetName, ItemStack stack) {
        ServerPlayer target = admin.getServer().getPlayerList().getPlayer(UUID.fromString(targetUuid));

        if (target != null) {
            target.getInventory().add(stack);
            target.sendSystemMessage(Component.literal("An admin restored an item to you."));
            AuditManager.logItemRestore(admin, targetName, stack);
        } else {
            admin.sendSystemMessage(Component.literal("Target is offline. Cannot restore directly."));
        }
    }

    private static void restoreFullInventory(ServerPlayer admin, String targetUuid, String targetName, List<ItemStack> items) {
        ServerPlayer target = admin.getServer().getPlayerList().getPlayer(UUID.fromString(targetUuid));

        if (target != null) {
            target.getInventory().clearContent();
            for (int i = 0; i < items.size(); i++) {
                if (i < target.getInventory().getContainerSize()) {
                    target.getInventory().setItem(i, items.get(i).copy());
                }
            }
            AuditManager.logFullRestore(admin, targetName);
        }
    }

    private static String resolveName(MinecraftServer server, String uuidStr) {
        try {
            return server.getProfileCache().get(UUID.fromString(uuidStr))
                    .map(profile -> profile.getName())
                    .orElse(uuidStr);
        } catch (IllegalArgumentException e) {
            return uuidStr;
        }
    }
}