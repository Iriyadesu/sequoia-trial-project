package sequoia.trial.command;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class LastSeenCommand {
    static String lastjoin = null;
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,CommandBuildContext commandBuildContext) {
        dispatcher.register(
                //! The name of the command
                ClientCommandManager.literal("lastseen")
                        //! Argument for the command (name, type)
                        .then(ClientCommandManager.argument("playername", StringArgumentType.string())
                                .executes(LastSeenCommand::execute))
        );
    }

    public static int execute(CommandContext<FabricClientCommandSource> context) {
        //! Have to parse the argument
        String playername = StringArgumentType.getString(context, "playername");
        //! CompletableFuture for asynchronous api call
        CompletableFuture<String> playerCompletableFuture = CallApi(playername);
        playerCompletableFuture.whenComplete((response,throwable) -> {
            //! If throwable == null there's no error, otherwise an error has occurred.
            if (throwable != null) { context.getSource().sendFeedback(Component.literal("An error has occurred while looking up the player.")); }
            else {

                if (response.matches("Unknown player")) { context.getSource().sendFeedback(Component.literal("Unkown player.")); }

                    //! Have to parse the response first to see if they are online or not, if they aren't we pass the json to GetOnlineString which returns the string in the correct format to send to the player.
                    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
                    String output = jsonObject.get("online").getAsBoolean() ? String.format("%s is currently online.", playername) : GetOnlineString(jsonObject,playername);

                    context.getSource().sendFeedback(Component.literal(output));
                }
        });

        return 1;
    }

    private static String GetOnlineString(JsonObject jsonObject, String playername) {
        lastjoin = jsonObject.get("lastJoin").getAsString();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        LocalDateTime lastJoinTime = LocalDateTime.parse(lastjoin, formatter);
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(lastJoinTime, now);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        return String.format("%s was last online %d days, %d hours, and %d minutes ago.", playername, days, hours, minutes);
    }

    private static CompletableFuture<String> CallApi(String PlayerName) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("https://api.wynncraft.com/v3/player/%s", PlayerName))).GET().build();
        HttpClient httpClient = HttpClient.newHttpClient();
        return httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    //! We found the player's profile
                    if (response.statusCode() ==200 && response.body() != null){
                        return response.body();
                    }
                    else {
                        //! We did not find the player's profile.
                        return "Unknown player";
                    }
                });
    }

}
