package pl.fratik.FratikDev.funkcje;

import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.core.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.core.requests.restaction.MessageAction;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.entity.WeryfikacjaInfo;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;
import pl.fratik.FratikDev.util.NetworkUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.awt.Color.decode;

public class Weryfikacja {

    private final ManagerBazyDanych managerBazyDanych;
    private boolean intervalLock = false;

    public Weryfikacja(ManagerBazyDanych managerBazyDanych, JDA jda) {
        this.managerBazyDanych = managerBazyDanych;
        Message zRegMes = jda.getTextChannelById(Config.instance.kanaly.zatwierdzRegulamin).getMessageById(Config.instance.wiadomosci.zatwierdzRegulaminWiadomosc).complete();
        if (zRegMes == null) throw new IllegalStateException("Brak pierwszej wiadomosci");
        try {zRegMes.clearReactions().complete();} catch (Exception ignored) {}
        zRegMes = jda.getTextChannelById(Config.instance.kanaly.zatwierdzRegulamin).getMessageById(Config.instance.wiadomosci.zatwierdzRegulaminWiadomosc).complete();
        zRegMes.addReaction(jda.getEmoteById(Config.instance.emotki.greenTick)).complete();
        zRegMes.addReaction(jda.getEmoteById(Config.instance.emotki.redTick)).complete();
        Executors.newScheduledThreadPool(2).scheduleWithFixedDelay(() -> {
            if (intervalLock) return;
            intervalLock = true;
            try {
                Map<Member, Typ> zabrano = new HashMap<>();
                Date dzisiaj = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(dzisiaj);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                dzisiaj = Date.from(cal.toInstant());
                for (Member mem : jda.getGuildById(Config.instance.guildId).getMembers().stream()
                        .filter(m -> m.getRoles().contains(jda.getRoleById(Config.instance.role.rolaUzytkownika)) &&
                                !m.getUser().isBot())
                        .collect(Collectors.toList())) {
                    WeryfikacjaInfo weryfikacjaInfo = managerBazyDanych.getWeryfikacja(mem.getUser());
                    if (weryfikacjaInfo == null) {
                        mem.getGuild().getController().removeSingleRoleFromMember(mem,
                                jda.getRoleById(Config.instance.role.rolaUzytkownika)).complete();
                        zabrano.put(mem, Typ.BRAKDANYCH);
                        continue;
                    }
                    Date data;
                    if (weryfikacjaInfo.getOstatniaWiadomosc() != null) data = weryfikacjaInfo.getOstatniaWiadomosc();
                    else data = weryfikacjaInfo.getWeryfikacja();
                    Calendar cal2 = Calendar.getInstance();
                    cal2.setTime(data);
                    cal2.set(Calendar.HOUR_OF_DAY, 0);
                    cal2.set(Calendar.MINUTE, 0);
                    cal2.set(Calendar.SECOND, 0);
                    cal2.set(Calendar.MILLISECOND, 0);
                    cal2.add(Calendar.DAY_OF_MONTH, 3);
                    Date koniec = Date.from(cal2.toInstant());
                    if (!dzisiaj.equals(koniec)) continue;
                    mem.getGuild().getController().removeSingleRoleFromMember(mem,
                        jda.getRoleById(Config.instance.role.rolaUzytkownika)).complete();
                    zabrano.put(mem, Typ.CZAS);
                }
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(decode("#00ff00"));
                eb.setAuthor("Czyszczenie nieaktywnych osób");
                eb.setTimestamp(Instant.now());
                eb.setDescription("Zabrano rolę");
                StringBuilder f1sb = new StringBuilder();
                StringBuilder f2sb = new StringBuilder();
                boolean f1end = false;
                boolean f2end = false;
                for (int i = 0; i < zabrano.size(); i++) {
                    Member mem = new ArrayList<>(zabrano.keySet()).get(i);
                    Typ typ = new ArrayList<>(zabrano.values()).get(i);
                    if (typ == Typ.CZAS) {
                        if (f1end) continue;
                        if (f1sb.length() >= 1000) {
                            f1sb.append("...i ").append(zabrano.values().stream().filter(v -> v == Typ.CZAS).count()
                                    - (i + 1)).append(" innym");
                            f1end = true;
                            continue;
                        }
                        f1sb.append(mem.getUser().getName()).append("#").append(mem.getUser().getDiscriminator());
                    } else {
                        if (f2end) continue;
                        if (f2sb.length() >= 1000) {
                            f2sb.append("...i ").append(zabrano.values().stream().filter(v -> v == Typ.BRAKDANYCH).count()
                                    - (i + 1)).append(" innym");
                            f2end = true;
                            continue;
                        }
                        f2sb.append(mem.getUser().getName()).append("#").append(mem.getUser().getDiscriminator());
                    }
                    if (i + 1 != zabrano.size()) {
                        if (typ == Typ.CZAS) f1sb.append("\n");
                        else f2sb.append("\n");
                    }
                }
                if (!f1sb.toString().isEmpty()) eb.addField("...z powodu 3d od ostatniej wiadomosci:", f1sb.toString(),
                        false);
                if (!f2sb.toString().isEmpty()) eb.addField("...z powodu braku danych:", f2sb.toString(), false);
                if (!f1sb.toString().isEmpty() || !f2sb.toString().isEmpty())
                    jda.getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).queue();
                intervalLock = false;
            } catch (Throwable t) {
                LoggerFactory.getLogger(Weryfikacja.class).error("coś nie pykło", t);
                intervalLock = false;
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    @Subscribe
    public void onMessage(MessageReceivedEvent e) {
        if (!e.getGuild().getId().equals(Config.instance.guildId)) return;
        User author = e.getAuthor();
        if (author.isBot()) return;
        WeryfikacjaInfo info = managerBazyDanych.getWeryfikacja(author);
        if (info == null) info = new WeryfikacjaInfo(author.getId());
        if (info.getWeryfikacja() == null) return;
        info.setOstatniaWiadomosc(Date.from(e.getMessage().getCreationTime().toInstant()));
        managerBazyDanych.save(info);
    }

    @Subscribe
    public void onMessageReactionAdd(MessageReactionAddEvent e) {
        if (!e.getReactionEmote().isEmote() || !e.getChannel().getId().equals(Config.instance.kanaly.zatwierdzRegulamin)) return;
        if (e.getUser().equals(e.getJDA().getSelfUser())) return;
        Message m = e.getChannel().getMessageById(e.getMessageId()).complete();
        if (!m.getId().equals(Config.instance.wiadomosci.zatwierdzRegulaminWiadomosc)) return;
        Emote emote = e.getReactionEmote().getEmote();
        Member member = e.getMember();
        if (emote.getId().equals(Config.instance.emotki.greenTick)) {
            //#region daty
            Date wczesniejsza = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(wczesniejsza);
            cal.set(Calendar.HOUR_OF_DAY, Config.instance.blokadaOd);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            wczesniejsza = Date.from(cal.toInstant());
            Date pozniejsza = new Date();
            Calendar cal2 = Calendar.getInstance();
            cal2.setTime(pozniejsza);
            cal2.set(Calendar.HOUR_OF_DAY, Config.instance.blokadaDo);
            cal2.set(Calendar.MINUTE, 0);
            cal2.set(Calendar.SECOND, 0);
            cal2.set(Calendar.MILLISECOND, 0);
            pozniejsza = Date.from(cal2.toInstant());
            Date pozniejsza1 = new Date();
            Calendar cal3 = Calendar.getInstance();
            cal3.setTime(pozniejsza1);
            cal3.set(Calendar.HOUR_OF_DAY, Config.instance.blokadaDo);
            cal3.set(Calendar.MINUTE, 0);
            cal3.set(Calendar.SECOND, 0);
            cal3.set(Calendar.MILLISECOND, 0);
            cal3.add(Calendar.DAY_OF_MONTH, 1);
            pozniejsza1 = Date.from(cal3.toInstant());
            Date teraz = new Date();
            cal.add(Calendar.DAY_OF_MONTH, -1);
            Date wczesniejsza1 = Date.from(cal.toInstant());
            //#endregion daty
            if ((teraz.after(wczesniejsza) && teraz.before(pozniejsza1)) || (teraz.after(wczesniejsza1) && teraz.before(pozniejsza))) {
                e.getChannel().sendMessage(e.getUser().getAsMention() + ", nie jest trochę za późno na " +
                        "weryfikację? Spróbuj ponownie w normalnej porze!").complete().delete().queueAfter(5, TimeUnit.SECONDS);
                e.getReaction().removeReaction(e.getUser()).complete();
                return;
            }
            if (member.getJoinDate().toInstant().toEpochMilli() - Instant.now().toEpochMilli() >= -300000) {
                e.getChannel().sendMessage("Ejejej, " + e.getUser().getAsMention() + "! " +
                        "Widzę co tam robisz, nawet 5 minut nie minęło odkąd dołączyłeś/aś tutaj! " +
                        "Nie ma szans byś w tak krótki okres czasu przeczytał(a) regulamin!").complete()
                        .delete().queueAfter(5, TimeUnit.SECONDS);
                e.getReaction().removeReaction(e.getUser()).complete();
                return;
            }
            WeryfikacjaInfo data = managerBazyDanych.getWeryfikacja(e.getUser());
            if (data != null) managerBazyDanych.usunWeryfikacje(e.getUser());
            data = new WeryfikacjaInfo(e.getUser().getId());
            data.setWeryfikacja(new Date());
            managerBazyDanych.save(data);
            String nowyNick = e.getUser().getName().replaceAll("[^A-Za-z0-9 ĄąĆćĘęŁłŃńÓóŚśŹźŻż./\\\\!@#$%^&*()_+\\-=\\[\\]';<>?,~`{}|\":]", "");
            Matcher matcher = Pattern.compile("^([^A-Za-z0-9ĄąĆćĘęŁłŃńÓóŚśŹźŻż]+)").matcher(nowyNick);
            boolean oho = matcher.find();
            if (oho) nowyNick = nowyNick.replaceFirst(Pattern.quote(matcher.group(1)), "");
            if (nowyNick.isEmpty()) nowyNick = "mam rakowy nick";
            if (e.getMember().getEffectiveName().equals(nowyNick)) {
                e.getChannel().sendMessage(e.getUser().getAsMention() + ", witamy w gronie zweryfikowanych! Główny kanał" +
                        " to <#" + Config.instance.kanaly.glownyKanal + "> btw.")
                        .complete().delete().queueAfter(5, TimeUnit.SECONDS);
            } else {
                e.getChannel().sendMessage(e.getUser().getAsMention() + ", witamy w gronie zweryfikowanych! Główny kanał" +
                        " to <#" + Config.instance.kanaly.glownyKanal + "> btw. Twój nick zawiera niedozwolone znaki, " +
                        "został on ustawiony na '" + nowyNick + "'. Jeżeli coś się nie podoba, zgłoś się __miło__ do " +
                        "administratora po zmianę nicku. Nie jest to nasz obowiązek.")
                        .complete().delete().queueAfter(5, TimeUnit.SECONDS);
                e.getGuild().getController().setNickname(e.getMember(), nowyNick).complete();
            }
            e.getGuild().getController()
                    .addSingleRoleToMember(member, e.getGuild().getRoleById(Config.instance.role.rolaUzytkownika)).complete();
            e.getReaction().removeReaction(e.getUser()).complete();
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(decode("#00ff00"));
            eb.setAuthor("Nowa osoba zweryfikowana");
            eb.setTimestamp(Instant.now());
            eb.setDescription("Zweryfikowała się osoba " + e.getUser().getAsMention() + "!");
            eb.addField("Data dołączenia na Discorda", new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(Date.from(e.getUser().getCreationTime().toInstant())), false);
            eb.addField("Data weryfikacji", new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(data.getWeryfikacja()), false);
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).queue();
        }
        if (emote.getId().equals(Config.instance.emotki.redTick)) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(decode("#00ff00"));
            eb.setAuthor("Osoba niezatwierdzila regulaminu");
            eb.setTimestamp(Instant.now());
            eb.setDescription("Osoba " + e.getUser().getAsMention() + " nie zatwierdzila regulaminu.");
            eb.addField("Data dołączenia na Discorda", new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(Date.from(e.getUser().getCreationTime().toInstant())), false);
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).complete();
            e.getGuild().getController().kick(member).reason("Niezatwierdzenie regulaminu").complete();
            e.getReaction().removeReaction(e.getUser()).complete();
        }
    }

    @Subscribe
    public void onUsernameChange(UserUpdateNameEvent e) {
        Member mem = e.getJDA().getGuildById(Config.instance.guildId).getMember(e.getUser());
        if (mem == null) return;
        if (!mem.getRoles().contains(e.getJDA()
                .getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika))) return;
        e.getJDA().getGuildById(Config.instance.guildId).getController().removeSingleRoleFromMember(mem,
                e.getJDA().getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika)).complete();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(decode("#00ff00"));
        eb.setAuthor("Osoba zmieniła nick");
        eb.setTimestamp(Instant.now());
        eb.setDescription("Zabrano rolę " + e.getUser().getAsMention() + " za zmianę nicku");
        eb.addField("Stary nick", e.getOldName(), true);
        eb.addField("Nowy nick", e.getNewName(), true);
        e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).complete();
    }

    @Subscribe
    public void onAvatarChange(UserUpdateAvatarEvent e) {
        Member mem = e.getJDA().getGuildById(Config.instance.guildId).getMember(e.getUser());
        if (mem == null) return;
        if (!mem.getRoles().contains(e.getJDA()
                .getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika))) return;
        e.getJDA().getGuildById(Config.instance.guildId).getController().removeSingleRoleFromMember(mem,
                e.getJDA().getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika)).complete();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(decode("#00ff00"));
        eb.setAuthor("Osoba zmieniła avatar");
        eb.setTimestamp(Instant.now());
        eb.setDescription("Zabrano rolę " + e.getUser().getAsMention() + " za zmianę avataru.");
        byte[] img = new byte[0];
        try {
            JSONObject zdjecie = NetworkUtil.getJson(Config.instance.api + "/api/polacz?zdjecie1=" +
                    URLEncoder.encode(e.getOldAvatarUrl().replace(".webp", ".png")
                            + "?size=2048", "UTF-8") + "&zdjecie2=" +
                    URLEncoder.encode(e.getNewAvatarUrl().replace(".webp", ".png")
                            + "?size=2048", "UTF-8"), Config.instance.apiKey);
            if (zdjecie == null) throw new IOException("brak zdjecia");
            img = NetworkUtil.getBytesFromBufferArray(zdjecie.getJSONObject("image").getJSONArray("data"));
            eb.appendDescription("\nNa zdjęciu po lewej jest stary avatar, po prawej nowy.");
            eb.setImage("attachment://avatary.png");
        } catch (IOException ignored) {
        }
        MessageAction akcja = e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build());
        if (img.length != 0)
            akcja.addFile(img, "avatary.png").complete();
        else akcja.complete();
    }

    private enum Typ {
        BRAKDANYCH, CZAS
    }

}
