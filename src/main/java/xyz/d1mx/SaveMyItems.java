package xyz.d1mx;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaveMyItems implements ModInitializer {
	public static final String MOD_ID = "smi";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static int BACKUP_INTERVAL_MINUTES = 10;
	private int tickCounter = 0;

	@Override
	public void onInitialize() {
		registerCommand();
		registerBackupTimer();
	}

	private void registerCommand() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
			dispatcher.register(Commands.literal("smi")
					.requires(source -> source.hasPermission(2))
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						SmiGui.openMainMenu(player);
						return 1;
					}));
		});
	}

	private void registerBackupTimer() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Pause timer if server is empty to save resources
			if (server.getPlayerList().getPlayerCount() == 0) return;

			tickCounter++;
			int ticksRequired = 20 * 60 * BACKUP_INTERVAL_MINUTES;

			if (tickCounter >= ticksRequired) {
				tickCounter = 0;
				performBackups(server);
			}
		});
	}

	private void performBackups(net.minecraft.server.MinecraftServer server) {
		int backupCount = 0;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Skip spectators - they don't have real inventories to save
			if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) continue;

			BackupManager.createBackup(player);
			backupCount++;
		}

		if (backupCount > 0) {
			LOGGER.info("[SMI] Backup cycle complete. Saved {} inventories.", backupCount);
		}
	}
}