package me.itstautvydas.velcmdforw;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class CommandUtil {
   private final VelocityCommandForward plugin;
   private final MessageUtil messageUtil;

   public CommandUtil(VelocityCommandForward plugin, MessageUtil messageUtil) {
      this.plugin = plugin;
      this.messageUtil = messageUtil;
   }

   @SuppressWarnings("unchecked")
   public LiteralCommandNode<CommandSourceStack> buildCustomCommand() {
      return ((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal(this.plugin.customCommandName)
            .then(((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal("reload")
                  .requires(sender -> sender.getSender().hasPermission("velocitycommandforward.admin")))
                  .executes(ctx -> {
                     this.plugin.saveDefaultConfig();
                     this.plugin.reloadConfig();
                     this.plugin.reloadFilter();
                     CommandSender sender = ctx.getSource().getSender();
                     this.messageUtil.sendMessage(sender, "messages.reload");
                     return 1;
                  }))
            .then(((RequiredArgumentBuilder<CommandSourceStack, String>) Commands
                  .argument("command", StringArgumentType.greedyString())
                  .requires(sender -> sender.getSender().hasPermission("velocitycommandforward.send")))
                  .executes(ctx -> {
                     String command = StringArgumentType.getString(ctx, "command");
                     this.handleCommand(ctx.getSource(), command);
                     return 1;
                  })))
            .build();
   }

   private void handleCommand(CommandSourceStack source, String command) {
      CommandSender sender = source.getSender();
      if (sender instanceof Player player) {
         this.handlePlayerCommand(player, command);
      } else if (sender instanceof ConsoleCommandSender) {
         this.handleConsoleCommand(sender, command);
      }
   }

   /**
    * Sends a command packet via the player's plugin messaging channel.
    * The sendPluginMessage() call is wrapped in runOnEntityRegion()
    * to ensure it executes on the player's owning region thread (Folia).
    */
   private void handlePlayerCommand(Player player, String command) {
      Map<String, String> placeholders = this.createPlaceholders(player.getName(), command);
      byte filter = this.messageUtil.shouldFilterCommand(command) ? (byte) 1 : (byte) 0;

      byte[] data = this.buildPacket(player.getUniqueId().toString(), command, filter, placeholders);

      // sendPluginMessage() must run on the entity's region thread in Folia
      this.plugin.runOnEntityRegion(player, () -> {
         player.sendPluginMessage(this.plugin, VelocityCommandForward.CHANNEL, data);
      });

      if (filter != 1) {
         this.plugin.log(player.getName(), command);
         this.messageUtil.sendMessage(player, "messages.command-sent-as-player", placeholders);
      }
   }

   /**
    * Sends a command packet as console by borrowing an online player's connection.
    * The sendPluginMessage() call is wrapped in runOnEntityRegion()
    * to ensure it executes on the borrowed player's owning region thread (Folia).
    */
   private void handleConsoleCommand(CommandSender sender, String command) {
      Map<String, String> placeholders = this.createPlaceholders(sender.getName(), command);
      byte filter = this.messageUtil.shouldFilterCommand(command) ? (byte) 1 : (byte) 0;

      Player onlinePlayer = Bukkit.getOnlinePlayers().stream()
            .findFirst()
            .orElse(null);

      if (onlinePlayer == null) {
         this.messageUtil.sendMessage(sender,
               this.plugin.getConfig().getString("messages.no-online-player"));
         return;
      }

      byte[] data = this.buildPacket("", command, filter, placeholders);

      // sendPluginMessage() must run on the entity's region thread in Folia
      this.plugin.runOnEntityRegion(onlinePlayer, () -> {
         onlinePlayer.sendPluginMessage(this.plugin, VelocityCommandForward.CHANNEL, data);
      });

      if (filter != 1) {
         this.plugin.log("CONSOLE", command);
         this.messageUtil.sendMessage(sender, "messages.command-sent-as-console", placeholders);
      }
   }

   /**
    * Builds the binary packet to send via plugin messaging.
    * This is pure data — no Bukkit API calls — so it's safe on any thread.
    */
   private byte[] buildPacket(String uuid, String command, byte filter, Map<String, String> placeholders) {
      ByteArrayDataOutput out = ByteStreams.newDataOutput();
      out.writeUTF(uuid);
      out.writeUTF(command);
      out.writeByte(filter);
      out.writeUTF(this.messageUtil.velocityLog("messages.velocity-log", placeholders));
      return out.toByteArray();
   }

   private Map<String, String> createPlaceholders(String sender, String command) {
      Map<String, String> placeholders = new HashMap<>();
      placeholders.put("command", command);
      placeholders.put("sender", sender);
      return placeholders;
   }
}
