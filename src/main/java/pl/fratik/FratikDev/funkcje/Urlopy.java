package pl.fratik.FratikDev.funkcje;

import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.MessageUpdateEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.entity.Urlop;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;
import pl.fratik.FratikDev.util.EventWaiter;
import pl.fratik.FratikDev.util.MessageWaiter;

import java.awt.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Urlopy {

    private final List<Message> ignoredDelete = new ArrayList<>();
    private final List<User> ignoredMessage = new ArrayList<>();
    private final ManagerBazyDanych managerBazyDanych;
    private final EventWaiter eventWaiter;
    private boolean intervalLock = false;

    public Urlopy(ManagerBazyDanych managerBazyDanych, EventWaiter eventWaiter, JDA jda) {
        this.managerBazyDanych = managerBazyDanych;
        this.eventWaiter = eventWaiter;
        Executors.newScheduledThreadPool(2).scheduleWithFixedDelay(() -> {
            try {
                if (intervalLock) return;
                intervalLock = true;
                Date dzisiaj = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(dzisiaj);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                dzisiaj = Date.from(cal.toInstant());
                for (Urlop u : managerBazyDanych.getAllUrlopy()) {
                    if (u.getDataOd().equals(dzisiaj)) {
                        if (!(u.getDataOd().toInstant().toEpochMilli() - u.getDataDo().toInstant().toEpochMilli() >= -1209600000)) {
                            jda.getGuildById(Config.instance.guildId).getController()
                                    .removeSingleRoleFromMember(jda.getGuildById(Config.instance.guildId).getMemberById(u.getId()),
                                            jda.getGuildById(Config.instance.guildId)
                                                    .getRoleById(Config.instance.role.globalAdmin)).complete();
                        }
                    }
                    if (u.getDataDo().equals(dzisiaj) && u.isValid()) {
                        u.setValid(false);
                        managerBazyDanych.save(u);
                        User user = jda.getUserById(u.getId());
                        Message m;
                        try {
                            jda.getGuildById(Config.instance.guildId).getController()
                                    .addSingleRoleToMember(jda.getGuildById(Config.instance.guildId).getMemberById(u.getId()),
                                            jda.getGuildById(Config.instance.guildId)
                                                    .getRoleById(Config.instance.role.globalAdmin)).complete();
                            m = jda.getTextChannelById(Config.instance.kanaly.urlopyGa).getMessageById(u.getMessageId()).complete();
                            if (m == null) continue;
                            ignoredDelete.add(m);
                            m.delete().complete();
                        } catch (Throwable ignored) {}
                        jda.getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                                new EmbedBuilder().setAuthor(user.getName() + "#" +
                                                user.getDiscriminator(), null,
                                        user.getEffectiveAvatarUrl().replace(".webp", ".png"))
                                        .setColor(Color.decode("#ff8c00")).setFooter("Koniec urlopu", null)
                                        .addField("Powód końca", "Czas się skończył!", false)
                                        .build()
                        ).queue();
                    }
                    Member mem = jda.getGuildById(Config.instance.guildId).getMemberById(u.getId());
                    if (u.getDataOd().before(new Date()) && u.getDataDo().after(new Date()) && u.isValid()) {
                        if (mem == null) continue;
                        String _nick = "[Wagary " + (int)
                                Math.floor((double) (dzisiaj.toInstant().toEpochMilli() - u.getDataDo()
                                        .toInstant().toEpochMilli()) / 1000 / 60 / 60 / 24) * -1 + "d] ";
                        String nick = _nick + mem.getEffectiveName().replaceAll("\\[Wagary \\d+d] ", "");
                        if (mem.getNickname() != null && mem.getNickname().startsWith(_nick)) continue;
                        if (nick.length() >= 32) nick = nick.substring(0, 29) + "...";
                        mem.getGuild().getController().setNickname(mem,  nick).complete();
                    } else {
                        if (mem == null) continue;
                        String effName = mem.getNickname();
                        if (effName == null) effName = "";
                        if (effName.startsWith("[Wagary")) {
                            mem.getGuild().getController().setNickname(mem, null).complete();
                        }
                    }
                    if (u.getCooldownTo() != null && u.getCooldownTo().equals(dzisiaj)) {
                        managerBazyDanych.usunUrlop(jda.getUserById(u.getId()));
                    }
                }
                intervalLock = false;
            } catch (Throwable t) {
                LoggerFactory.getLogger(Urlopy.class).error("coś nie pykło", t);
                intervalLock = false;
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    @Subscribe
    public void onMessage(MessageReceivedEvent event) {
        if (!event.getMessage().getChannel().getId().equals(Config.instance.kanaly.urlopyGa)) return;
        if (event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) return;
        if (ignoredMessage.contains(event.getAuthor())) {
            ignoredMessage.remove(event.getAuthor());
            return;
        }
        Message msg = event.getMessage();
        //#region matcher'y
        Matcher matcherOd;
        Matcher matcherDo;
        try {
            matcherOd = Pattern.compile("od:? ?(0?[1-9]|[12]\\d|3[01])[./\\-](0?[1-9]|1[0-2])[./\\-](19|20\\d{2})",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
                    .matcher(event.getMessage().getContentRaw().split("\n")[0]);
            matcherDo = Pattern.compile("do:? ?(0?[1-9]|[12]\\d|3[01])[./\\-](0?[1-9]|1[0-2])[./\\-](19|20\\d{2})",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
                    .matcher(event.getMessage().getContentRaw().split("\n")[2]);
        } catch (Exception e) {
            ignoredDelete.add(msg);
            msg.delete().queue();
            Message _temp = msg.getChannel().sendMessage(msg.getAuthor().getAsMention() + ": Nieprawidłowy format!").complete();
            event.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                    new EmbedBuilder().setAuthor(msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator(),
                            null, msg.getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                            .addField("Powód odrzucenia", "Nieprawidłowy format", false)
                            .addField("Osoba odrzucająca", event.getJDA().getSelfUser().getAsMention(), false)
                            .build()
            ).queue();
            _temp.delete().queueAfter(5, TimeUnit.SECONDS);
            return;
        }
        if (!matcherOd.matches() || !matcherDo.matches()) {
            ignoredDelete.add(msg);
            msg.delete().queue();
            Message _temp = msg.getChannel().sendMessage(msg.getAuthor().getAsMention() + ": Nieprawidłowy format!").complete();
            event.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                    new EmbedBuilder().setAuthor(msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator(),
                            null, msg.getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                            .addField("Powód odrzucenia", "Nieprawidłowy format", false)
                            .addField("Osoba odrzucająca", event.getJDA().getSelfUser().getAsMention(), false)
                            .build()
            ).queue();
            _temp.delete().queueAfter(5, TimeUnit.SECONDS);
            return;
        }
        String[] matchesOd = new String[matcherOd.groupCount()];
        for (int i = 0; i < matcherOd.groupCount(); i++) {
            matchesOd[i] = matcherOd.group(i + 1);
        }
        String[] matchesDo = new String[matcherDo.groupCount()];
        for (int i = 0; i < matcherDo.groupCount(); i++) {
            matchesDo[i] = matcherDo.group(i + 1);
        }
        Date dataOd;
        Date dataDo;
        try {
            dataOd = new SimpleDateFormat("dd/MM/yyyy").parse(matchesOd[0] + "/" + matchesOd[1] + "/" + matchesOd[2]);
            dataDo = new SimpleDateFormat("dd/MM/yyyy").parse(matchesDo[0] + "/" + matchesDo[1] + "/" + matchesDo[2]);
        } catch (ParseException e) {
            ignoredDelete.add(msg);
            msg.delete().queue();
            Message _temp = msg.getChannel().sendMessage(msg.getAuthor().getAsMention() + ": Nieprawidłowy format!").complete();
            event.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                    new EmbedBuilder().setAuthor(msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator(),
                            null, msg.getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                            .addField("Powód odrzucenia", "Nieprawidłowy format", false)
                            .addField("Osoba odrzucająca", event.getJDA().getSelfUser().getAsMention(), false)
                            .build()
            ).queue();
            _temp.delete().queueAfter(5, TimeUnit.SECONDS);
            return;
        }
        //#endregion matcher'y
        if (dataOd.toInstant().toEpochMilli() - dataDo.toInstant().toEpochMilli() >= -259200000) {
            ignoredDelete.add(msg);
            msg.delete().queue();
            Message _temp = msg.getChannel().sendMessage(msg.getAuthor().getAsMention() + ": Urlop musi trwać dłużej niż 3 dni by być zarejestrowany!").complete();
            event.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                    new EmbedBuilder().setAuthor(msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator(),
                            null, msg.getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                            .addField("Powód odrzucenia", "Różnica dat krótsza od 3 dni", false)
                            .addField("Osoba odrzucająca", event.getJDA().getSelfUser().getAsMention(), false)
                            .build()
            ).queue();
            _temp.delete().queueAfter(5, TimeUnit.SECONDS);
            return;
        }
        Urlop data = managerBazyDanych.getUrlop(msg.getAuthor());
        if (data != null && data.isValid()) {
            ignoredDelete.add(msg);
            msg.delete().queue();
            Message _temp = msg.getChannel().sendMessage(msg.getAuthor().getAsMention() +
                    ": Masz już jakiś urlop w toku!").complete();
            _temp.delete().queueAfter(5, TimeUnit.SECONDS);
            return;
        }
        if (data != null && data.getCooldownTo() != null) {
            ignoredDelete.add(msg);
            msg.delete().queue();
            Message _temp;
            if (data.getCooldownTo() == null) throw new IllegalStateException("cooldownTo == null");
            _temp = msg.getChannel().sendMessage(msg.getAuthor().getAsMention() +
                    ": Twój urlop jest na cooldownie przez "
                    + (int) Math.floor((double) (Instant.now().toEpochMilli() -
                    data.getCooldownTo().toInstant().toEpochMilli()) / 1000 / 60 / 60 / 24) * -1 + " dni!").complete();
            event.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                    new EmbedBuilder().setAuthor(msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator(),
                            null, msg.getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                            .addField("Powód odrzucenia", "Cooldown", false)
                            .addField("Osoba odrzucająca", event.getJDA().getSelfUser().getAsMention(), false)
                            .build()
            ).queue();
            _temp.delete().queueAfter(5, TimeUnit.SECONDS);
            return;
        }
        data = new Urlop(msg.getAuthor().getId(), dataOd, dataDo, msg.getId());
        Date d = new Date(dataDo.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(14).toEpochDay() * 86400000);
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        d = Date.from(cal.toInstant());
        data.setCooldownTo(d);
        msg.addReaction(msg.getJDA().getEmoteById(Config.instance.emotki.greenTick)).queue();
        msg.addReaction(msg.getJDA().getEmoteById(Config.instance.emotki.redTick)).queue();
        msg.getChannel().sendMessage(msg.getGuild().getRoleById(Config.instance.role.zarzadzajacyGa).getAsMention()).queue();
        managerBazyDanych.save(data);
    }

    @Subscribe
    public void onMessageReactionAdd(MessageReactionAddEvent e) {
        if (!(e.getReactionEmote().isEmote() &&
                (e.getReactionEmote().getEmote().getId().equals(Config.instance.emotki.greenTick) ||
                        e.getReactionEmote().getEmote().getId().equals(Config.instance.emotki.redTick))))
            return;
        if (!e.getChannel().getId().equals(Config.instance.kanaly.urlopyGa)) return;
        if (e.getUser().getId().equals(e.getJDA().getSelfUser().getId())) return;
        Message msg = e.getChannel().getMessageById(e.getMessageId()).complete();
        if (e.getUser().equals(msg.getAuthor())
                || !msg.getGuild().getMemberById(e.getUser().getId()).getRoles()
                .contains(msg.getGuild().getRoleById(Config.instance.role.zarzadzajacyGa))) {
            e.getReaction().removeReaction(e.getUser()).queue();
            return;
        }
        e.getChannel().getIterableHistory().stream().limit(1000).filter(m -> m.getContentRaw()
                .equals(msg.getGuild().getRoleById(Config.instance.role.zarzadzajacyGa).getAsMention()))
                .forEach(m -> {
                    ignoredDelete.add(m);
                    m.delete().queue();
                });
        if (e.getReactionEmote().getEmote().getId().equals(Config.instance.emotki.greenTick)) {
            Urlop data = managerBazyDanych.getUrlop(msg.getAuthor());
            if (data == null || data.isValid()) return;
            data.setValid(true);
            data.setZatwierdzone(true);
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                    new EmbedBuilder().setAuthor(msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator(),
                            null, msg.getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#00ff00")).setFooter("Zatwierdzony urlop", null)
                            .addField("Osoba zatwierdzająca", e.getUser().getAsMention(), false)
                            .build()
            ).queue();
            msg.clearReactions().queue();
            try {
                if (data.getDataOd().toInstant().toEpochMilli() - data.getDataDo().toInstant().toEpochMilli() >= -1209600000)
                    msg.getAuthor().openPrivateChannel().complete()
                            .sendMessage("Twój urlop został przyjęty. \uD83C\uDFD6").complete();
                else {
                    Date dzisiaj = new Date();
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(dzisiaj);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    dzisiaj = Date.from(cal.toInstant());
                    if (data.getDataOd().equals(dzisiaj)) msg.getGuild().getController()
                            .removeSingleRoleFromMember(msg.getMember(),
                                    msg.getGuild().getRoleById(Config.instance.role.globalAdmin)).complete();
                    msg.getAuthor().openPrivateChannel().complete()
                            .sendMessage("Twój urlop został przyjęty, a rola zgodnie z regulaminem została/zostanie usunięta na okres urlopu. \uD83C\uDFD6").complete();
                }
            } catch (Exception ignored) {}
       }
        if (e.getReactionEmote().getEmote().getId().equals(Config.instance.emotki.redTick)) {
            zdobadzPowod(e.getUser(), e.getChannel(), "odrzucenia urlopu", _powod -> {
                String powod = _powod;
                if (powod == null) powod = "brak powodu";
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                        new EmbedBuilder().setAuthor(msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator(),
                                null, msg.getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                                .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                                .addField("Powód odrzucenia", powod, false)
                                .addField("Osoba odrzucająca", e.getUser().getAsMention(), false)
                                .build()
                ).queue();
                ignoredDelete.add(msg);
                msg.delete().queue();
                managerBazyDanych.usunUrlop(msg.getAuthor());
                try {
                    msg.getAuthor().openPrivateChannel().complete()
                            .sendMessage("Twój urlop został odrzucony.").complete();
                } catch (Exception ignored) {}
            });
        }
    }

    @Subscribe
    public void onMessageEdit(MessageUpdateEvent e) {
        if (!e.getChannel().getId().equals(Config.instance.kanaly.urlopyGa)) return;
        e.getChannel().sendMessage(e.getAuthor().getAsMention() + ": Nie można edytować urlopów! Usuwam Twój urlop!")
                .complete().delete().queueAfter(5, TimeUnit.SECONDS);
        e.getMessage().delete().queue();
        e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                new EmbedBuilder().setAuthor(e.getMessage().getAuthor().getName() + "#" +
                                e.getMessage().getAuthor().getDiscriminator(), null,
                        e.getMessage().getAuthor().getEffectiveAvatarUrl().replace(".webp", ".png"))
                        .setColor(Color.decode("#ff8c00")).setFooter("Anulowany urlop", null)
                        .addField("Powód anulowania", "Edytowanie wiadomości", false)
                        .build()
        ).queue();
        e.getGuild().getController()
                .addSingleRoleToMember(e.getMember(),
                        e.getGuild().getRoleById(Config.instance.role.globalAdmin)).complete();
        Urlop data = managerBazyDanych.getUrlop(e.getAuthor());
        data.setValid(false);
        managerBazyDanych.save(data);
    }

    @Subscribe
    public void onMessageDelete(MessageDeleteEvent e) {
        if (!e.getChannel().getId().equals(Config.instance.kanaly.urlopyGa)) return;
        if (ignoredDelete.stream().map(ISnowflake::getId).anyMatch(a -> a.equals(e.getMessageId()))) {
            ignoredDelete.remove(ignoredDelete.stream().filter(a -> a.getId().equals(e.getMessageId())).findAny().orElse(null));
            return;
        }
        Urlop data = null;
        for (Urlop _data : managerBazyDanych.getAllUrlopy()) {
            if (_data.getMessageId().equals(e.getMessageId())) data = _data;
        }
        if (data == null) {
//            e.getChannel().sendMessage("Nie znaleziono urlopu...").complete().delete().queueAfter(10, TimeUnit.SECONDS);
            return;
        }
        Urlop finalData = data;
        zdobadzPowod(e.getJDA().getUserById(data.getId()), e.getChannel(), "anulowania urlopu", _powod -> {
            String powod = _powod;
            if (powod == null) powod = "brak powodu";
            if (powod.length() >= 1000) {
                e.getChannel().sendMessage("Powód jest powyżej 1k znaków. Używam \"brak powodu\".").complete().delete().queueAfter(5, TimeUnit.SECONDS);
                powod = "brak powodu";
            }
            finalData.setValid(false);
            managerBazyDanych.save(finalData);
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessage(
                    new EmbedBuilder().setAuthor(e.getJDA().getUserById(finalData.getId()).getName() + "#" +
                                    e.getJDA().getUserById(finalData.getId()).getDiscriminator(),
                            null, e.getJDA().getUserById(finalData.getId()).getEffectiveAvatarUrl()
                                    .replace(".webp", ".png"))
                            .setColor(Color.decode("#ff8c00")).setFooter("Anulowany urlop | usunięcie wiadomości", null)
                            .addField("Powód anulowania", powod, false)
                            .build()
            ).queue();
            e.getGuild().getController()
                    .addSingleRoleToMember(e.getGuild().getMemberById(finalData.getId()),
                            e.getGuild().getRoleById(Config.instance.role.globalAdmin)).complete();
        });
    }

    private void zdobadzPowod(User user, MessageChannel channel, String akcja, Consumer<String> callback) {
        Message _tmp = channel.sendMessage(user.getAsMention() + ": Powód " + akcja + "?").complete();
        MessageWaiter.Context ctx = new MessageWaiter.Context(user, channel);
        MessageWaiter waiter = new MessageWaiter(eventWaiter, ctx);
        waiter.setTimeoutHandler(() -> {
            ignoredMessage.remove(user);
            _tmp.delete().queue();
            callback.accept("brak powodu");
        });
        ignoredMessage.add(user);
        waiter.setMessageHandler((m) -> {
            ignoredDelete.add(m.getMessage());
            ignoredDelete.add(_tmp);
            _tmp.delete().queue();
            m.getMessage().delete().complete();
            ignoredMessage.remove(user);
            callback.accept(m.getMessage().getContentRaw());
        });
        waiter.create();
    }

}
