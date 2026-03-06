package me.itstautvydas.velcmdforw;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class MessageUtil {
   private final JavaPlugin plugin;
   private volatile List<String> filteredCommands;
   private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

   public MessageUtil(JavaPlugin plugin) {
      this.plugin = plugin;
      this.filteredCommands = new CopyOnWriteArrayList<>(plugin.getConfig().getStringList("filtered-commands"));
   }

   public void sendMessage(CommandSender sender, String key) {
      this.sendMessage(sender, key, new HashMap<>());
   }

   public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
      Component message = this.getMessage(key, placeholders);
      if (!message.equals(Component.empty())) {
         sender.sendMessage(message);
      }
   }

   public Component getMessage(String key, Map<String, String> placeholders) {
      String rawMessage = this.plugin.getConfig().getString(key);
      if (rawMessage != null) {
         String processedMessage = replacePlaceholders(rawMessage, placeholders);
         return LegacyComponentSerializer.legacyAmpersand().deserialize(processedMessage);
      } else {
         return PlainTextComponentSerializer.plainText().deserialize("Missing message key: " + key);
      }
   }

   public void consoleLog(String key, Map<String, String> placeholders) {
      Component log = this.getMessage(key, placeholders);
      if (!log.equals(Component.empty())) {
         this.plugin.getLogger().info(LegacyComponentSerializer.legacySection().serialize(log));
      }
   }

   public String velocityLog(String key, Map<String, String> placeholders) {
      return LegacyComponentSerializer.legacySection().serialize(this.getMessage(key, placeholders));
   }

   private static String replacePlaceholders(String message, Map<String, String> placeholders) {
      if (placeholders.isEmpty()) {
         return message;
      }
      StringBuilder result = new StringBuilder();
      Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
      while (matcher.find()) {
         String placeholder = matcher.group(1);
         String replacement = placeholders.getOrDefault(placeholder, matcher.group());
         matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
      }
      matcher.appendTail(result);
      return result.toString();
   }

   public void updateFilteredCommands(List<String> commands) {
      this.filteredCommands = new CopyOnWriteArrayList<>(commands);
   }

   public boolean shouldFilterCommand(String command) {
      List<String> snapshot = this.filteredCommands;
      if (snapshot.isEmpty()) {
         return false;
      }
      String root = command.split(" ", 2)[0].replace("/", "").toLowerCase(Locale.ROOT);
      for (String filtered : snapshot) {
         if (filtered.equalsIgnoreCase(root)) {
            return true;
         }
      }
      return false;
   }
}
