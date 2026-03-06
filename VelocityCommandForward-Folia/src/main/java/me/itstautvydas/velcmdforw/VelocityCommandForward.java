package me.itstautvydas.velcmdforw;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus.Internal;

public final class VelocityCommandForward extends JavaPlugin {
   public static final String CHANNEL = "velocity_command_forward:main";

   private static final boolean FOLIA;

   static {
      boolean folia;
      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         folia = true;
      } catch (ClassNotFoundException e) {
         folia = false;
      }
      FOLIA = folia;
   }

   public String customCommandName;
   public List<String> filteredCommands;
   private MessageUtil messageUtil;
   private Log4jFilterUtil log4jFilterUtil;

   @Override
   @Internal
   public void onEnable() {
      this.getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
      this.saveDefaultConfig();
      this.getConfig().options().copyDefaults(true);
      this.saveConfig();
      this.customCommandName = this.getConfig().getString("custom-command", "pcm");
      this.filteredCommands = this.getConfig().getStringList("filtered-commands");
      this.messageUtil = new MessageUtil(this);
      this.log4jFilterUtil = new Log4jFilterUtil(this, this.customCommandName);
      CommandUtil commandUtil = new CommandUtil(this, this.messageUtil);
      this.registerLogFilter();
      this.registerCommand(commandUtil.buildCustomCommand());
      this.getLogger().info("Custom Command: " + this.customCommandName);
      this.getLogger().info("Folia detected: " + FOLIA);
   }

   public void registerCommand(LiteralCommandNode<CommandSourceStack> commandNode) {
      this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (commands) -> {
         commands.registrar().register(commandNode);
      });
   }

   private void registerLogFilter() {
      LoggerContext context = (LoggerContext) LogManager.getContext(false);
      Configuration config = context.getConfiguration();
      this.removeLogFilters(config);
      this.log4jFilterUtil = new Log4jFilterUtil(this, this.customCommandName);
      config.getRootLogger().addFilter(this.log4jFilterUtil);
      context.updateLoggers(config);
   }

   private void removeLogFilters(Configuration config) {
      Filter existing = config.getRootLogger().getFilter();
      if (existing instanceof Log4jFilterUtil) {
         config.getRootLogger().removeFilter(existing);
      }
   }

   public void reloadFilter() {
      List<String> filteredCommands = this.getConfig().getStringList("filtered-commands");
      this.log4jFilterUtil.updateFilteredCommands(filteredCommands);
      this.messageUtil.updateFilteredCommands(filteredCommands);
   }

   public void log(String sender, String command) {
      if (!this.messageUtil.shouldFilterCommand(command)) {
         Map<String, String> placeholders = new HashMap<>();
         placeholders.put("sender", sender);
         placeholders.put("command", command);
         this.messageUtil.consoleLog("messages.console-log", placeholders);
      }
   }

   // ── Folia-compatible scheduling ──────────────────────────────────────

   /**
    * Returns true if the server is running Folia (regionized multithreading).
    */
   public static boolean isFolia() {
      return FOLIA;
   }

   /**
    * Runs a task on the region thread that owns the given entity.
    * On Paper this falls back to the main-thread scheduler.
    */
   public void runOnEntityRegion(Entity entity, Runnable runnable) {
      if (FOLIA) {
         entity.getScheduler().run(this, task -> runnable.run(), null);
      } else {
         Bukkit.getScheduler().runTask(this, runnable);
      }
   }

   /**
    * Runs a task asynchronously (off any tick thread).
    * Uses Folia's AsyncScheduler when available, otherwise Bukkit's async
    * scheduler.
    */
   public void runAsync(Runnable runnable) {
      if (FOLIA) {
         Bukkit.getAsyncScheduler().runNow(this, task -> runnable.run());
      } else {
         Bukkit.getScheduler().runTaskAsynchronously(this, runnable);
      }
   }

   /**
    * Runs a task on the global region (Folia) or main thread (Paper).
    * Use for operations that don't belong to any specific entity/region.
    */
   public void runOnGlobalRegion(Runnable runnable) {
      if (FOLIA) {
         Bukkit.getGlobalRegionScheduler().run(this, task -> runnable.run());
      } else {
         Bukkit.getScheduler().runTask(this, runnable);
      }
   }

   @Override
   public void onDisable() {
      LoggerContext context = (LoggerContext) LogManager.getContext(false);
      Configuration config = context.getConfiguration();
      this.removeLogFilters(config);
      context.updateLoggers();
   }
}
