package pl.fratik.FratikDev.funkcje;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.Main;
import pl.fratik.FratikDev.entity.Urlop;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Urlopy {

    private static final String CREATE_BUTTON_ID = "CREATE";
    private static final String CREATE_MODAL_ID = "CREATE";
    private static final String CREATE_MODAL_FROM = "OD";
    private static final String CREATE_MODAL_TO = "DO";
    private static final String CREATE_MODAL_REASON = "POWOD";
    private static final String ACCEPT_BUTTON_ID = "ACCEPT";
    private static final String REJECT_BUTTON_ID = "REJECT";
    private static final String REJECT_MODAL_ID = "REJECT";
    private static final String REJECT_MODAL_REASON = "REASON";
    private static final String DELETE_BUTTON_ID = "DELETE";
    private static final String DELETE_MODAL_ID = "DELETE";
    private static final String DELETE_MODAL_REASON = "REASON";

    private final ManagerBazyDanych managerBazyDanych;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    private boolean intervalLock = false;

    public Urlopy(ManagerBazyDanych managerBazyDanych, JDA jda) {
        this.managerBazyDanych = managerBazyDanych;
        final TextChannel chan = jda.getTextChannelById(Config.instance.kanaly.urlopyGa);
        if (chan == null) throw new IllegalStateException("Nie znaleziono kanału urlopyGa");
        if (Config.instance.wiadomosci.urlopWiadomoscBota == null ||
                chan.retrieveMessageById(Config.instance.wiadomosci.urlopWiadomoscBota).complete() == null)
            setup(chan);
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        executorService.scheduleWithFixedDelay(() -> {
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
                    Member mem = jda.getGuildById(Config.instance.guildId).getMemberById(u.getId());
                    if (mem == null) {
                        managerBazyDanych.usunUrlop(u.getId());
                        return;
                    }
                    if (u.getDataOd().equals(dzisiaj) &&
                            u.getDataOd().toInstant().toEpochMilli() - u.getDataDo().toInstant().toEpochMilli() < -1209600000) {
                        jda.getGuildById(Config.instance.guildId)
                                .removeRoleFromMember(mem, jda.getGuildById(Config.instance.guildId)
                                        .getRoleById(Config.instance.role.globalAdmin)).complete();
                    }
                    if (u.getDataDo().equals(dzisiaj) && u.isValid()) {
                        u.setValid(false);
                        managerBazyDanych.save(u);
                        User user = jda.getUserById(u.getId());
                        Message m;
                        try {
                            jda.getGuildById(Config.instance.guildId)
                                    .addRoleToMember(mem, jda.getGuildById(Config.instance.guildId)
                                                    .getRoleById(Config.instance.role.globalAdmin)).complete();
                            m = jda.getTextChannelById(Config.instance.kanaly.urlopyGa).retrieveMessageById(u.getMessageId()).complete();
                            if (m == null) continue;
                            m.delete().complete();
                        } catch (Exception ignored) {}
                        jda.getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                                new EmbedBuilder().setAuthor(user.getName() + "#" +
                                                user.getDiscriminator(), null,
                                        user.getEffectiveAvatarUrl().replace(".webp", ".png"))
                                        .setColor(Color.decode("#ff8c00")).setFooter("Koniec urlopu", null)
                                        .addField("Powód końca", "Czas się skończył!", false)
                                        .build()
                        ).queue();
                    }
                    if (u.getDataOd().before(new Date()) && u.getDataDo().after(new Date()) && u.isValid()) {
                        String _nick = "[Wagary " + (int)
                                Math.floor((double) (dzisiaj.toInstant().toEpochMilli() - u.getDataDo()
                                        .toInstant().toEpochMilli()) / 1000 / 60 / 60 / 24) * -1 + "d] ";
                        String nick = _nick + mem.getEffectiveName().replaceAll("\\[Wagary \\d+d] ", "");
                        if (mem.getNickname() != null && mem.getNickname().startsWith(_nick)) continue;
                        if (nick.length() >= 32) nick = nick.substring(0, 29) + "...";
                        try {
                            mem.getGuild().modifyNickname(mem,  nick).complete();
                        } catch (Exception e) {
                            LoggerFactory.getLogger(Urlopy.class).error("nie udało się zmienić nicku dla " + mem, e);
                        }
                    } else {
                        String effName = mem.getNickname();
                        if (effName == null) effName = "";
                        if (effName.startsWith("[Wagary")) {
                            try {
                                mem.getGuild().modifyNickname(mem, null).complete();
                            } catch (Exception e) {
                                LoggerFactory.getLogger(Urlopy.class).error("nie udało się zmienić nicku dla " + mem, e);
                            }
                        }
                    }
                    if (u.getCooldownTo() != null && u.getCooldownTo().before(new Date())) {
                        managerBazyDanych.usunUrlop(jda.getUserById(u.getId()));
                    }
                }
            } catch (Throwable t) {
                LoggerFactory.getLogger(Urlopy.class).error("coś nie pykło", t);
            } finally {
                intervalLock = false;
            }
        }, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdownNow));
    }

    private void setup(TextChannel chan) {
        Message sent = chan.sendMessage(Config.instance.wiadomosci.urlopTresc)
                .setActionRow(Button.primary(CREATE_BUTTON_ID, "Utwórz urlop")).complete();
        Config.instance.wiadomosci.urlopWiadomoscBota = sent.getId();
        try {
            File cfg = new File("config.json");
            Files.write(cfg.toPath(), Main.GSON.toJson(Config.instance).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).error("Nie udało się zapisać poprawionej konfiguracji!", e);
        }
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onButtonClick(ButtonInteractionEvent e) {
        if (!e.getChannel().getId().equals(Config.instance.kanaly.urlopyGa)) return;
        if (e.getUser().equals(e.getJDA().getSelfUser())) return;
        if (e.getComponentId().equals(CREATE_BUTTON_ID)) {
            TextInput from = TextInput.create(CREATE_MODAL_FROM, "Data od (DD-MM-YYYY)", TextInputStyle.SHORT)
                    .setRequiredRange(10, 10).build();
            TextInput to = TextInput.create(CREATE_MODAL_TO, "Data do (DD-MM-YYYY)", TextInputStyle.SHORT)
                    .setRequiredRange(10, 10).build();
            TextInput reason = TextInput.create(CREATE_MODAL_REASON, "Powód urlopu", TextInputStyle.PARAGRAPH)
                    .setRequiredRange(1, 1000).build();
            e.replyModal(Modal.create(CREATE_MODAL_ID, "Zgłoś urlop")
                    .addActionRows(ActionRow.of(from), ActionRow.of(to), ActionRow.of(reason))
                    .build()).complete();
        }
        if (e.getComponentId().equals(ACCEPT_BUTTON_ID)) {
            Message msg = e.getMessage();
            Urlop data = managerBazyDanych.getUrlopByMessageId(msg.getId());
            if (data == null || data.isValid()) return;
            if (e.getUser().getId().equals(data.getId()) ||
                    !e.getMember().getRoles().contains(e.getGuild().getRoleById(Config.instance.role.zarzadzajacyGa))) {
                e.reply("Nie masz uprawnień do zatwierdzenia tego urlopu!").setEphemeral(true).complete();
                return;
            }
            e.deferEdit().queue();
            User user = e.getJDA().retrieveUserById(data.getId()).complete();
            data.setValid(true);
            data.setZatwierdzone(true);
            managerBazyDanych.save(data);
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                    new EmbedBuilder().setAuthor(user.getName() + "#" + user.getDiscriminator(),
                                    null, user.getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#00ff00")).setFooter("Zatwierdzony urlop", null)
                            .addField("Urlop ważny od", sdf.format(data.getDataOd()), true)
                            .addField("Urlop ważny do", sdf.format(data.getDataDo()), true)
                            .addField("Powód urlopu", data.getPowod(), false)
                            .addField("Osoba zatwierdzająca", e.getUser().getAsMention(), false)
                            .build()
            ).queue();
            e.getMessage().editMessageEmbeds(generateEmbed(data, e.getJDA()))
                    .setActionRows(ActionRow.of(Button.danger(DELETE_BUTTON_ID, Emoji.fromUnicode("\uD83D\uDDD1"))))
                    .override(true).queue();
            try {
                if (data.getDataOd().toInstant().toEpochMilli() - data.getDataDo().toInstant().toEpochMilli() >= -1209600000)
                    user.openPrivateChannel().flatMap(pv -> pv.sendMessage("Twój urlop został przyjęty. \uD83C\uDFD6")).queue(null, x -> {});
                else {
                    Date dzisiaj = new Date();
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(dzisiaj);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    dzisiaj = Date.from(cal.toInstant());
                    if (data.getDataOd().equals(dzisiaj)) msg.getGuild().removeRoleFromMember(msg.getMember(),
                            msg.getGuild().getRoleById(Config.instance.role.globalAdmin)).complete();
                    user.openPrivateChannel().flatMap(pv -> pv.sendMessage("Twój urlop został przyjęty, a rola " +
                            "zgodnie z regulaminem została/zostanie usunięta na okres urlopu. \uD83C\uDFD6"))
                            .queue(null, x -> {});
                }
            } catch (Exception ignored) {}
        }
        if (e.getComponentId().equals(REJECT_BUTTON_ID)) {
            Message msg = e.getMessage();
            Urlop data = managerBazyDanych.getUrlopByMessageId(msg.getId());
            if (data == null) return;
            if (e.getUser().getId().equals(data.getId()) ||
                    !e.getMember().getRoles().contains(e.getGuild().getRoleById(Config.instance.role.zarzadzajacyGa))) {
                e.reply("Nie masz uprawnień do odrzucenia tego urlopu!").setEphemeral(true).complete();
                return;
            }
            e.replyModal(Modal.create(REJECT_MODAL_ID + e.getMessageId(), "Odrzucanie urlopu")
                    .addActionRow(TextInput.create(REJECT_MODAL_REASON, "Powód odrzucenia urlopu", TextInputStyle.PARAGRAPH)
                            .setMaxLength(1000).build())
                    .build()).complete();
        }
        if (e.getComponentId().equals(DELETE_BUTTON_ID)) {
            Message msg = e.getMessage();
            Urlop data = managerBazyDanych.getUrlopByMessageId(msg.getId());
            if (data == null) return;
            if (!e.getUser().getId().equals(data.getId())) {
                e.reply("Tylko autor urlopu może go anulować!").setEphemeral(true).complete();
                return;
            }
            e.replyModal(Modal.create(DELETE_MODAL_ID + msg.getId(), "Anulowanie urlopu")
                    .addActionRow(TextInput.create(DELETE_MODAL_REASON, "Powód anulowania", TextInputStyle.PARAGRAPH)
                            .setMaxLength(1000).build())
                    .build()).complete();
        }
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onModal(ModalInteractionEvent e) {
        if (!e.getChannel().getId().equals(Config.instance.kanaly.urlopyGa)) return;
        if (e.getUser().equals(e.getJDA().getSelfUser())) return;
        if (e.getModalId().equals(CREATE_MODAL_ID)) {
            String from = e.getValue(CREATE_MODAL_FROM).getAsString();
            String to = e.getValue(CREATE_MODAL_TO).getAsString();
            String reason = e.getValue(CREATE_MODAL_REASON).getAsString();
            Matcher matcherFrom = Pattern.compile("(0?[1-9]|[12]\\d|3[01])[./\\-](0?[1-9]|1[0-2])[./\\-](19|20\\d{2})",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL).matcher(from);
            Matcher matcherTo = Pattern.compile("(0?[1-9]|[12]\\d|3[01])[./\\-](0?[1-9]|1[0-2])[./\\-](19|20\\d{2})",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL).matcher(to);
            if (!matcherFrom.matches() || !matcherTo.matches()) {
                e.reply("Nieprawidłowy format!").setEphemeral(true).complete();
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                        new EmbedBuilder().setAuthor(e.getUser().getName() + "#" + e.getUser().getDiscriminator(),
                                        null, e.getUser().getEffectiveAvatarUrl().replace(".webp", ".png"))
                                .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                                .addField("Urlop ważny od", from, true)
                                .addField("Urlop ważny do", to, true)
                                .addField("Powód urlopu", reason, false)
                                .addField("Powód odrzucenia", "Nieprawidłowy format", false)
                                .addField("Osoba odrzucająca", e.getJDA().getSelfUser().getAsMention(), false)
                                .build()
                ).queue();
                return;
            }
            String[] matchesFrom = new String[matcherFrom.groupCount()];
            for (int i = 0; i < matcherFrom.groupCount(); i++) {
                matchesFrom[i] = matcherFrom.group(i + 1);
            }
            String[] matchesTo = new String[matcherTo.groupCount()];
            for (int i = 0; i < matcherTo.groupCount(); i++) {
                matchesTo[i] = matcherTo.group(i + 1);
            }
            Date dateFrom;
            Date dateTo;
            try {
                dateFrom = sdf.parse(matchesFrom[0] + "/" + matchesFrom[1] + "/" + matchesFrom[2]);
                dateTo = sdf.parse(matchesTo[0] + "/" + matchesTo[1] + "/" + matchesTo[2]);
            } catch (ParseException ex) {
                e.reply("Nieprawidłowy format!").setEphemeral(true).complete();
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                        new EmbedBuilder().setAuthor(e.getUser().getName() + "#" + e.getUser().getDiscriminator(),
                                        null, e.getUser().getEffectiveAvatarUrl().replace(".webp", ".png"))
                                .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                                .addField("Urlop ważny od", from, true)
                                .addField("Urlop ważny do", to, true)
                                .addField("Powód urlopu", reason, false)
                                .addField("Powód odrzucenia", "Nieprawidłowy format", false)
                                .addField("Osoba odrzucająca", e.getJDA().getSelfUser().getAsMention(), false)
                                .build()
                ).queue();
                return;
            }
            if (dateFrom.toInstant().toEpochMilli() - dateTo.toInstant().toEpochMilli() >= -259200000) {
                e.reply("Urlop musi trwać dłużej niż 3 dni by być zarejestrowany!").setEphemeral(true).complete();
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                        new EmbedBuilder().setAuthor(e.getUser().getName() + "#" + e.getUser().getDiscriminator(),
                                        null, e.getUser().getEffectiveAvatarUrl().replace(".webp", ".png"))
                                .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                                .addField("Urlop ważny od", sdf.format(dateFrom), true)
                                .addField("Urlop ważny do", sdf.format(dateTo), true)
                                .addField("Powód urlopu", reason, false)
                                .addField("Powód odrzucenia", "Różnica dat krótsza od 3 dni", false)
                                .addField("Osoba odrzucająca", e.getJDA().getSelfUser().getAsMention(), false)
                                .build()
                ).queue();
                return;
            }
            Urlop data = managerBazyDanych.getUrlop(e.getUser());
            if (data != null && data.isValid()) {
                e.reply("Masz już jakiś urlop w toku!").setEphemeral(true).complete();
                return;
            }
            if (data != null && data.getCooldownTo() != null) {
                if (data.getCooldownTo() == null) throw new IllegalStateException("cooldownTo == null");
                e.reply("Twój urlop jest na cooldownie przez " + (int) Math.floor((double) (Instant.now().toEpochMilli() -
                        data.getCooldownTo().toInstant().toEpochMilli()) / 1000 / 60 / 60 / 24) * -1 + " dni!").complete();
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                        new EmbedBuilder().setAuthor(e.getUser().getName() + "#" + e.getUser().getDiscriminator(),
                                        null, e.getUser().getEffectiveAvatarUrl().replace(".webp", ".png"))
                                .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                                .addField("Urlop ważny od", sdf.format(data.getDataOd()), true)
                                .addField("Urlop ważny do", sdf.format(data.getDataDo()), true)
                                .addField("Powód urlopu", reason, false)
                                .addField("Powód odrzucenia", "Cooldown", false)
                                .addField("Osoba odrzucająca", e.getJDA().getSelfUser().getAsMention(), false)
                                .build()
                ).queue();
                return;
            }
            e.deferEdit().complete();
            Role zga = e.getGuild().getRoleById(Config.instance.role.zarzadzajacyGa);
            data = new Urlop(e.getUser().getId(), dateFrom, dateTo, reason, null);
            Message msg = e.getMessageChannel().sendMessage("**Urlop niezatwierdzony!** " + zga.getAsMention())
                    .setEmbeds(generateEmbed(data, e.getJDA()))
                    .mention(zga)
                    .setActionRows(ActionRow.of(
                            Button.success(ACCEPT_BUTTON_ID, "Akceptuj"), Button.danger(REJECT_BUTTON_ID, "Odrzuć"))
                    ).complete();
            data.setMessageId(msg.getId());
            Date d = new Date(dateTo.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(14).toEpochDay() * 86400000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            d = Date.from(cal.toInstant());
            data.setCooldownTo(d);
            managerBazyDanych.save(data);
        }
        if (e.getModalId().startsWith(REJECT_MODAL_ID)) {
            String reason = e.getValue(REJECT_MODAL_REASON).getAsString();
            Message msg;
            try {
                String messageId = e.getModalId().substring(REJECT_MODAL_ID.length());
                msg = e.getMessageChannel().retrieveMessageById(messageId).complete();
            } catch (Exception ignored) {
                return;
            }
            Urlop data = managerBazyDanych.getUrlopByMessageId(msg.getId());
            if (data == null || data.isValid()) return;
            User user = e.getJDA().retrieveUserById(data.getId()).complete();
            e.deferEdit().queue();
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                    new EmbedBuilder().setAuthor(user.getName() + "#" + user.getDiscriminator(),
                                    null, user.getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#ff0000")).setFooter("Odrzucony urlop", null)
                            .addField("Urlop ważny od", sdf.format(data.getDataOd()), false)
                            .addField("Urlop ważny do", sdf.format(data.getDataDo()), false)
                            .addField("Powód urlopu", data.getPowod(), false)
                            .addField("Powód odrzucenia", reason, false)
                            .addField("Osoba odrzucająca", e.getUser().getAsMention(), false)
                            .build()
            ).queue();
            msg.delete().queue();
            managerBazyDanych.usunUrlop(user);
            try {
                user.openPrivateChannel().complete()
                        .sendMessage("Twój urlop został odrzucony. Powód podany przez ZGA:\n" + reason).complete();
            } catch (Exception ignored) {}
        }
        if (e.getModalId().startsWith(DELETE_MODAL_ID)) {
            String reason = e.getValue(DELETE_MODAL_REASON).getAsString();
            Message msg;
            try {
                String messageId = e.getModalId().substring(DELETE_MODAL_ID.length());
                msg = e.getMessageChannel().retrieveMessageById(messageId).complete();
            } catch (Exception ignored) {
                return;
            }
            e.deferEdit().queue();
            msg.delete().queue();
            Urlop data = managerBazyDanych.getUrlopByMessageId(msg.getId());
            data.setValid(false);
            managerBazyDanych.save(data);
            User user = e.getJDA().retrieveUserById(data.getId()).complete();
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiUrlopow).sendMessageEmbeds(
                    new EmbedBuilder().setAuthor(user.getAsTag(),
                                    null, user.getEffectiveAvatarUrl().replace(".webp", ".png"))
                            .setColor(Color.decode("#ff8c00")).setFooter("Anulowany urlop | usunięcie wiadomości", null)
                            .addField("Urlop ważny od", sdf.format(data.getDataOd()), true)
                            .addField("Urlop ważny do", sdf.format(data.getDataDo()), true)
                            .addField("Powód urlopu", data.getPowod(), false)
                            .addField("Powód anulowania", reason, false)
                            .build()
            ).queue();
            e.getGuild().addRoleToMember(user, e.getGuild().getRoleById(Config.instance.role.globalAdmin)).queue();
        }
    }

    private MessageEmbed generateEmbed(Urlop urlop, JDA jda) {
        User user = jda.retrieveUserById(urlop.getId()).complete();
        return new EmbedBuilder()
                .setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl())
                .addField("Urlop ważny od", sdf.format(urlop.getDataOd()), true)
                .addField("Urlop ważny do", sdf.format(urlop.getDataDo()), true)
                .addField("Powód urlopu", urlop.getPowod(), false)
                .build();
    }

}
