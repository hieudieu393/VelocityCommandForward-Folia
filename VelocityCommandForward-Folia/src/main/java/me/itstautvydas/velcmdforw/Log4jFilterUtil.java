package me.itstautvydas.velcmdforw;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.bukkit.plugin.java.JavaPlugin;

public class Log4jFilterUtil extends AbstractFilter {
   private final String customCommandName;
   private volatile List<String> filteredCommands;

   public Log4jFilterUtil(JavaPlugin plugin, String customCommandName) {
      this.customCommandName = customCommandName;
      this.filteredCommands = new CopyOnWriteArrayList<>(plugin.getConfig().getStringList("filtered-commands"));
   }

   public void updateFilteredCommands(List<String> commands) {
      this.filteredCommands = new CopyOnWriteArrayList<>(commands);
   }

   @Override
   public Result filter(LogEvent event) {
      Message msg = event.getMessage();
      if (msg == null) {
         return Result.NEUTRAL;
      }
      String message = msg.getFormattedMessage();
      if (message == null) {
         return Result.NEUTRAL;
      }

      // Fast path: if no commands are filtered or the message doesn't even
      // mention the custom command, skip the loop entirely.
      List<String> snapshot = this.filteredCommands;
      if (snapshot.isEmpty()) {
         return Result.NEUTRAL;
      }

      String prefix = " issued server command: /" + this.customCommandName + " ";
      if (!message.contains(prefix)) {
         return Result.NEUTRAL;
      }

      // Check if any filtered command matches
      for (String command : snapshot) {
         if (message.contains(prefix + command)) {
            return Result.DENY;
         }
      }
      return Result.NEUTRAL;
   }
}
