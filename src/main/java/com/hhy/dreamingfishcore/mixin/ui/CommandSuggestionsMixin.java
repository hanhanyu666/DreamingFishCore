package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** Keeps command completions and usage hints clear of the raised custom chat input panel. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    private EditBox input;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Redirect(
            method = {"showSuggestions", "renderUsage"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;height:I")
    )
    private int dreamingfish$anchorAboveCustomInput(Screen screen) {
        return ImmersiveChatManager.commandSuggestionScreenHeight(screen.height);
    }

    @Redirect(
            method = "updateCommandInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientSuggestionProvider;getCustomTabSugggestions()Ljava/util/Collection;"
            )
    )
    private Collection<String> dreamingfish$suggestMentionedPlayers(ClientSuggestionProvider provider) {
        if (dreamingfish$currentToken().startsWith("@")) {
            return provider.getOnlinePlayerNames().stream()
                    .map(playerName -> "@" + playerName)
                    .toList();
        }
        return provider.getCustomTabSugggestions();
    }

    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void dreamingfish$showMentionSuggestions(CallbackInfo ci) {
        if (minecraft.options.autoSuggestions().get() && dreamingfish$currentToken().startsWith("@")) {
            ((CommandSuggestions) (Object) this).showSuggestions(false);
        }
    }

    private String dreamingfish$currentToken() {
        String value = input.getValue();
        int cursor = Math.max(0, Math.min(input.getCursorPosition(), value.length()));
        int tokenStart = cursor;
        while (tokenStart > 0 && !Character.isWhitespace(value.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        return value.substring(tokenStart, cursor);
    }
}
