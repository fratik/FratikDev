package pl.fratik.FratikDev;

import com.google.common.eventbus.EventBus;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.EventListener;

class JDAEventHandler implements EventListener {
    private final EventBus eventBus;

    JDAEventHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void onEvent(Event event) {

        if (event instanceof MessageReceivedEvent) {
            eventBus.post(event);
        } else {
            eventBus.post(event);
        }
    }

}