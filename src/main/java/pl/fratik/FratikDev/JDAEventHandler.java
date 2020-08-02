package pl.fratik.FratikDev;

import com.google.common.eventbus.EventBus;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

class JDAEventHandler implements EventListener {
    private final EventBus eventBus;

    JDAEventHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof MessageReceivedEvent) {
            eventBus.post(event);
        } else {
            eventBus.post(event);
        }
    }

}