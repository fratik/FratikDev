package pl.fratik.FratikDev.util;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MessageWaiter {
    private final EventWaiter eventWaiter;
    private final Context context;

    @Getter @Setter private Consumer<MessageReceivedEvent> messageHandler;
    @Getter @Setter private Runnable timeoutHandler;

    public MessageWaiter(EventWaiter eventWaiter, Context context) {
        this.eventWaiter = eventWaiter;
        this.context = context;
    }

    public void create() {
        eventWaiter.waitForEvent(MessageReceivedEvent.class, this::checkMessage,
                this::handleMessage, 30, TimeUnit.SECONDS, this::onTimeout);
    }

    private boolean checkMessage(MessageReceivedEvent event) {
        return event.getTextChannel() != null && event.getTextChannel().equals(context.getChannel())
                && event.getAuthor().equals(context.getUser());
    }

    private void handleMessage(MessageReceivedEvent event) {
        if (messageHandler != null) {
            messageHandler.accept(event);
        }
    }

    private void onTimeout() {
        if (timeoutHandler != null) timeoutHandler.run();
    }

    @Getter
    static public class Context {
        @NotNull private final User user;
        @NotNull private final MessageChannel channel;

        public Context(@NotNull User user, @NotNull MessageChannel channel) {
            this.user = user;
            this.channel = channel;
        }
    }
}
