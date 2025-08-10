package com.chetana.gravesafe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GraveSafe implements ModInitializer {
    public static final String MOD_ID = "gravesafe";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final File DEATHS_FILE = new File("config/" + MOD_ID + "/deaths.txt");

    @Override
    public void onInitialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && oldPlayer.getWorld().isServer()) {
                double x = oldPlayer.getX();
                double y = oldPlayer.getY();
                double z = oldPlayer.getZ();

                String coords = "X: " + Math.round(x) + " Y: " + Math.round(y) + " Z: " + Math.round(z);
                String tpCommand = "/tp " + newPlayer.getName().getString() + " " +
                        Math.round(x) + " " + Math.round(y) + " " + Math.round(z);

                MutableText clickableMessage = Text.literal("You died at ")
                        .formatted(Formatting.RED, Formatting.BOLD)
                        .append(Text.literal(coords)
                                .formatted(Formatting.YELLOW)
                                .styled(style -> style.withClickEvent(
                                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand)
                                )));

                newPlayer.sendMessage(clickableMessage, false);
                saveDeathToFile(newPlayer, coords);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("deathlog")
                .executes(ctx -> showDeathLog(ctx.getSource())));
        });
    }

    private void saveDeathToFile(ServerPlayerEntity player, String coords) {
        try {
            File dir = new File("config/" + MOD_ID);
            if (!dir.exists()) dir.mkdirs();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(DEATHS_FILE, true))) {
                String time = LocalDateTime.now().format(TIME_FORMAT);
                writer.write(player.getName().getString() + " | " + time + " | " + coords);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save death coordinates: " + e.getMessage());
        }
    }

    private int showDeathLog(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can use this command."));
            return 0;
        }

        List<String> lines = readDeathLog(player.getName().getString());

        if (lines.isEmpty()) {
            player.sendMessage(Text.literal("No recorded deaths found.").formatted(Formatting.GRAY), false);
        } else {
            player.sendMessage(Text.literal("--- Your Death History ---").formatted(Formatting.GOLD, Formatting.BOLD), false);
            for (String line : lines) {
                String[] parts = line.split(" \| ");
                if (parts.length == 2) {
                    String time = parts[0];
                    String coords = parts[1];

                    String[] coordParts = coords.replace("X:", "").replace("Y:", "").replace("Z:", "").split(" ");
                    if (coordParts.length >= 3) {
                        String tpCommand = "/tp " + player.getName().getString() + " " +
                                coordParts[0] + " " + coordParts[1] + " " + coordParts[2];

                        MutableText clickableCoords = Text.literal(coords)
                                .formatted(Formatting.YELLOW)
                                .styled(style -> style.withClickEvent(
                                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand)
                                ));

                        player.sendMessage(
                            Text.literal(time + " | ").formatted(Formatting.AQUA)
                                .append(clickableCoords),
                            false
                        );
                    }
                }
            }
        }
        return 1;
    }

    private List<String> readDeathLog(String playerName) {
        List<String> list = new ArrayList<>();
        if (!DEATHS_FILE.exists()) return list;

        try (BufferedReader reader = new BufferedReader(new FileReader(DEATHS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(playerName + " |")) {
                    list.add(line.replaceFirst(playerName + " \| ", ""));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read death log: " + e.getMessage());
        }
        return list;
    }
}
