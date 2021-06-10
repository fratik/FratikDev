package pl.fratik.FratikDev.funkcje;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.Main;
import pl.fratik.FratikDev.entity.WeryfikacjaInfo;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;
import pl.fratik.FratikDev.util.NetworkUtil;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.awt.Color.decode;

public class Weryfikacja {

    public static boolean wymuszoneOdblokowanie = false;
    private final ManagerBazyDanych managerBazyDanych;
    private boolean intervalLock = false;

    public Weryfikacja(ManagerBazyDanych managerBazyDanych, JDA jda) {
        this.managerBazyDanych = managerBazyDanych;
        final TextChannel chan = jda.getTextChannelById(Config.instance.kanaly.zatwierdzRegulamin);
        if (chan == null) throw new IllegalStateException("Nie znaleziono kanału zatwierdzRegulamin");
        if (Config.instance.wiadomosci.zatwierdzRegulaminWiadomoscBota == null ||
                chan.retrieveMessageById(Config.instance.wiadomosci.zatwierdzRegulaminWiadomoscBota).complete() == null)
            setup(chan);
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        executorService.scheduleWithFixedDelay(() -> {
            if (intervalLock || !Config.instance.funkcje.weryfikacja.zabierzRole) return;
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
                        mem.getGuild().removeRoleFromMember(mem,
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
                    mem.getGuild().removeRoleFromMember(mem,
                        jda.getRoleById(Config.instance.role.rolaUzytkownika)).complete();
                    zabrano.put(mem, Typ.CZAS);
                }
                if (!Config.instance.funkcje.weryfikacja.logi) return;
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
            } catch (Exception t) {
                LoggerFactory.getLogger(Weryfikacja.class).error("coś nie pykło", t);
                intervalLock = false;
            }
        }, 60, 60, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdownNow));
    }

    private void setup(TextChannel chan) {
        Message sent = chan.sendMessage(Config.instance.wiadomosci.zatwierdzRegulaminTresc)
                .setActionRow(
                        Button.success("ACCEPT", "Akceptuję regulamin"),
                        Button.danger("REJECT", "Nie akceptuję regulaminu")
                ).complete();
        Config.instance.wiadomosci.zatwierdzRegulaminWiadomoscBota = sent.getId();
        try {
            File cfg = new File("config.json");
            Files.write(cfg.toPath(), Main.GSON.toJson(Config.instance).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).error("Nie udało się zapisać poprawionej konfiguracji!", e);
        }
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onMessage(MessageReceivedEvent e) {
        if (!e.getGuild().getId().equals(Config.instance.guildId)) return;
        User author = e.getAuthor();
        if (author.isBot()) return;
        WeryfikacjaInfo info = managerBazyDanych.getWeryfikacja(author);
        if (info == null) info = new WeryfikacjaInfo(author.getId());
        if (info.getWeryfikacja() == null) return;
        info.setOstatniaWiadomosc(Date.from(e.getMessage().getTimeCreated().toInstant()));
        managerBazyDanych.save(info);
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onMessageReactionAdd(ButtonClickEvent e) {
        if (!e.getChannel().getId().equals(Config.instance.kanaly.zatwierdzRegulamin)) return;
        if (e.getUser().equals(e.getJDA().getSelfUser())) return;
        Message m = e.getChannel().retrieveMessageById(e.getMessageId()).complete();
        if (!m.getId().equals(Config.instance.wiadomosci.zatwierdzRegulaminWiadomoscBota)) return;
        Member member = e.getMember();
        if (e.getComponentId().equals("ACCEPT")) {
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
            if (!wymuszoneOdblokowanie && Config.instance.funkcje.weryfikacja.restrykcje) {
                if ((teraz.after(wczesniejsza) && teraz.before(pozniejsza1)) || (teraz.after(wczesniejsza1) &&
                        teraz.before(pozniejsza))) {
                    e.reply("Nie jest trochę za późno na weryfikację? Spróbuj ponownie w normalnej porze!")
                            .setEphemeral(true).complete();
                    return;
                }
                OffsetDateTime dataUz = member.getUser().getTimeCreated();
                if (dataUz.toInstant().toEpochMilli() - Instant.now().toEpochMilli() >= -604800000) {
                    Calendar inst = Calendar.getInstance();
                    inst.setTime(new Date(dataUz.toInstant().toEpochMilli()));
                    inst.add(Calendar.WEEK_OF_MONTH, 1);
                    String exp = "za " + Math.abs(ChronoUnit.DAYS.between(inst.getTime().toInstant(), Instant.now()))
                            + " dni!";
                    e.reply("Przykro mi, ale Twoje konto na Discord " +
                            "musi mieć co najmniej tydzień. Spróbuj ponownie " + exp).setEphemeral(true).complete();
                    return;
                }
                if (member.getTimeJoined().toInstant().toEpochMilli() - Instant.now().toEpochMilli() >= -300000) {
                    e.reply("Ejejej, widzę co tam robisz! Nawet 5 minut nie minęło odkąd dołączyłeś/aś tutaj! " +
                            "Nie ma szans byś w tak krótki okres czasu przeczytał(a) regulamin!")
                            .setEphemeral(true).complete();
                    return;
                }
            }
            WeryfikacjaInfo data = managerBazyDanych.getWeryfikacja(e.getUser());
            if (data == null) {
                data = new WeryfikacjaInfo(e.getUser().getId());
            } else {
                data.setIleRazy(data.getIleRazy() + 1);
                data.setOstatniaWiadomosc(null);
            }
            data.setWeryfikacja(new Date());
            managerBazyDanych.save(data);
            String nowyNick = e.getMember().getEffectiveName();
            if (Config.instance.funkcje.weryfikacja.naprawNick) {
                nowyNick = e.getUser().getName().replaceAll("[^A-Za-z0-9 ĄąĆćĘęŁłŃńÓóŚśŹźŻż./\\\\!@#$%^&*()_+\\-=\\[\\]';<>?,~`{}|\":]", "");
                Matcher matcher = Pattern.compile("^([^A-Za-z0-9ĄąĆćĘęŁłŃńÓóŚśŹźŻż]+)").matcher(nowyNick);
                boolean oho = matcher.find();
                if (oho) nowyNick = nowyNick.replaceFirst(Pattern.quote(matcher.group(1)), "");
                if (nowyNick.isEmpty()) nowyNick = Config.instance.funkcje.weryfikacja.domyslnyNick;
            }
            if (e.getMember().getEffectiveName().equals(nowyNick)) {
                e.reply("Witamy w gronie zweryfikowanych! Główny kanał to " +
                        "<#" + Config.instance.kanaly.glownyKanal + "> btw.").setEphemeral(true).complete();
            } else {
                e.reply("Witamy w gronie zweryfikowanych! Główny kanał to " +
                        "<#" + Config.instance.kanaly.glownyKanal + "> btw. Twój nick zawiera niedozwolone znaki, " +
                        "został on ustawiony na '" + nowyNick + "'. Jeżeli coś się nie podoba, zgłoś się __miło__ do " +
                        "administratora po zmianę nicku. Nie jest to nasz obowiązek.").setEphemeral(true).complete();
                e.getGuild().modifyNickname(e.getMember(), nowyNick).complete();
            }
            e.getGuild().addRoleToMember(member, e.getGuild().getRoleById(Config.instance.role.rolaUzytkownika))
                    .completeAfter(5, TimeUnit.SECONDS);
            if (!Config.instance.funkcje.weryfikacja.logi) return;
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(decode("#00ff00"));
            eb.setAuthor("Nowa osoba zweryfikowana");
            eb.setTimestamp(Instant.now());
            eb.setDescription("Zweryfikowała się osoba " + e.getUser().getAsMention() + " (" + e.getUser().getName()
                    + "#" + e.getUser().getDiscriminator() + ", " + e.getUser().getId() + ") !");
            eb.addField("Data dołączenia na Discorda", new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(Date.from(e.getUser().getTimeCreated().toInstant())), false);
            eb.addField("Data weryfikacji", new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(data.getWeryfikacja()), false);
            if (wymuszoneOdblokowanie) eb.addField("Odblokowanie zostalo wymuszone", "Osoba nie powinna być wpuszczona!", false);
            if (data.getIleRazy() == 1) eb.addField("Ilość weryfikacji", "Jest to pierwsza weryfikacja tego użytkownika.", false);
            else eb.addField("Ilość weryfikacji", "Jest to " + data.getIleRazy() + " weryfikacja tego użytkownika.", false);
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).queue();
        }
        if (e.getComponentId().equals("REJECT")) {
            e.deferEdit().queue();
            e.getGuild().kick(member).reason("Niezatwierdzenie regulaminu").complete();
            if (!Config.instance.funkcje.weryfikacja.logi) return;
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(decode("#00ff00"));
            eb.setAuthor("Osoba niezatwierdzila regulaminu");
            eb.setTimestamp(Instant.now());
            eb.setDescription("Osoba " + e.getUser().getAsMention() + " nie zatwierdzila regulaminu.");
            eb.addField("Data dołączenia na Discorda", new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(Date.from(e.getUser().getTimeCreated().toInstant())), false);
            e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).complete();
        }
    }

    @Subscribe
    public void onUsernameChange(UserUpdateNameEvent e) {
        Member mem = e.getJDA().getGuildById(Config.instance.guildId).getMember(e.getUser());
        if (mem == null) return;
        if (!mem.getRoles().contains(e.getJDA()
                .getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika))) return;
        e.getJDA().getGuildById(Config.instance.guildId).removeRoleFromMember(mem,
                e.getJDA().getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika)).complete();
        if (!Config.instance.funkcje.weryfikacja.logi) return;
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
        if (!Config.instance.funkcje.weryfikacja.zabierzRolePrzyZmianieAvataru) return;
        Member mem = e.getJDA().getGuildById(Config.instance.guildId).getMember(e.getUser());
        if (mem == null) return;
        if (!mem.getRoles().contains(e.getJDA()
                .getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika))) return;
        e.getJDA().getGuildById(Config.instance.guildId).removeRoleFromMember(mem,
                e.getJDA().getGuildById(Config.instance.guildId).getRoleById(Config.instance.role.rolaUzytkownika)).complete();
        if (!Config.instance.funkcje.weryfikacja.logi) return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(decode("#00ff00"));
        eb.setAuthor("Osoba zmieniła avatar");
        eb.setTimestamp(Instant.now());
        eb.setDescription("Zabrano rolę " + e.getUser().getAsMention() + " za zmianę avataru.");
        byte[] img = new byte[0];
        try {
            JsonObject zdjecie = NetworkUtil.getJson(Config.instance.api + "/api/polacz?zdjecie[]=" +
                    URLEncoder.encode(e.getOldAvatarUrl().replace(".webp", ".png")
                            + "?size=2048", "UTF-8") + "&zdjecie[]=" +
                    URLEncoder.encode(e.getNewAvatarUrl().replace(".webp", ".png")
                            + "?size=2048", "UTF-8"), Config.instance.apiKey);
            if (zdjecie == null) throw new IOException("brak zdjecia");
            img = NetworkUtil.getBytesFromBufferArray(zdjecie.getAsJsonObject("image").getAsJsonArray("data"));
            eb.appendDescription("\nNa zdjęciu po lewej jest stary avatar, po prawej nowy.");
            eb.setImage("attachment://avatary.png");
        } catch (Exception ignored) {
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
