package pl.fratik.FratikDev.funkcje;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.utils.AllowedMentions;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.entity.SuffixData;
import pl.fratik.FratikDev.entity.WeryfikacjaInfo;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.awt.Color.decode;

public class Komendy {
    private final Weryfikacja weryfikacja;
    private final ManagerBazyDanych mbd;
    private NickJob nickJob;

    public Komendy(Weryfikacja weryfikacja, ManagerBazyDanych mbd, JDA jda) {
        this.weryfikacja = weryfikacja;
        this.mbd = mbd;
        Guild fdev = jda.getGuildById(Config.instance.guildId);
        if (fdev == null) throw new NullPointerException();
        List<CommandData> adminCommands = new ArrayList<>();
        List<CommandData> allCommands = new ArrayList<>();
        allCommands.add(new CommandData("wersja", "Wersja bota"));
        if (Config.instance.funkcje.komendy.suffix) {
            adminCommands.add(new CommandData("suffix", "Dodaje tekst do nicku każdej osoby na serwerze")
                    .addOption(OptionType.STRING, "tekst", "Tekst do dodania", true));
            adminCommands.add(new CommandData("usunsuffix", "Usuwa tekst do nicku każdej osoby na serwerze")
                    .addOption(OptionType.STRING, "tekst", "Tekst do usunięcia", true));
        }
        if (Config.instance.funkcje.komendy.naprawnicki) {
            adminCommands.add(new CommandData("naprawnicki", "Naprawia nicki wszystkich osób na serwerze"));
        }
        if (Config.instance.funkcje.komendy.weryfikacja) {
            adminCommands.add(new CommandData("weryfikacja", "Włącza/wyłącza weryfikację"));
        }
        if (Config.instance.funkcje.weryfikacja.zezwolNaZmianeNicku) {
            allCommands.add(new CommandData("nick", "Zmienia Twój nick na serwerze")
                    .addOption(OptionType.STRING, "nick", "Nowy nick (nie wypełniaj by usunąć)", false));
            if (Config.instance.funkcje.komendy.ustawNick) {
                adminCommands.add(new CommandData("ustawnick", "Zmienia nick podanej osoby")
                        .addOption(OptionType.USER, "osoba", "Osoba", true)
                        .addOption(OptionType.STRING, "nick", "Nowy nick (nie wypełniaj by usunąć)", false));
            }
            if (Config.instance.funkcje.komendy.blacklistNick) {
                adminCommands.add(new CommandData("blacklistnick", "Blokuje/odblokowuje możliwość zmiany nicku podanej osobie")
                        .addOption(OptionType.USER, "osoba", "Osoba", true));
            }
        }
        adminCommands.forEach(c -> c.setDefaultEnabled(false));
        List<Command> commands = fdev.updateCommands().addCommands(adminCommands).addCommands(allCommands).complete();
        commands.stream().filter(c -> adminCommands.stream().anyMatch(d -> d.getName().equals(c.getName())))
                .forEach(c -> c.updatePrivileges(fdev, CommandPrivilege.enableRole(Config.instance.role.admin)).complete());
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onCommand(SlashCommandEvent e) {
        switch (e.getName()) {
            case "wersja": {
                e.reply("FratikDev " + getClass().getPackage().getImplementationVersion()).setEphemeral(true).complete();
                break;
            }
            case "suffix":
            case "usunsuffix": {
                if (!Config.instance.funkcje.komendy.suffix) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                String suffix = e.getOption("tekst").getAsString();
                if (nickJob != null) {
                    e.reply("Operacja aktualizacji nicków jest już w toku.").complete();
                    return;
                }
                String cnt;
                Function<Member, String> modifier;
                if (e.getName().equals("suffix")) {
                    cnt = "Rozpoczęto dodawanie suffixów!";
                    modifier = m -> m.getEffectiveName() + ' ' + suffix;
                    mbd.save(new SuffixData(e.getGuild().getId(), suffix));
                } else if (e.getName().equals("usunsuffix")) {
                    cnt = "Rozpoczęto usuwanie suffixów!";
                    modifier = m -> {
                        if (m.getNickname() != null && m.getNickname().endsWith(' ' + suffix))
                            return m.getNickname().substring(0, m.getNickname().length() - (suffix.length() + 1));
                        else return m.getEffectiveName();
                    };
                    mbd.usunSuffix(e.getGuild());
                } else throw new IllegalArgumentException(e.getName());
                e.reply(new MessageBuilder(cnt).setActionRows(ActionRow.of(
                        Button.primary("NICK_PROGRESS", "Sprawdź postęp"),
                        Button.danger("NICK_STOP", "Przerwij")
                )).build()).complete();
                nickJob = new NickJob(e.getGuild(), modifier, job -> {
                    if (!e.getHook().isExpired()) e.getHook().sendMessage("Gotowe! " + job.getProgress()).complete();
                });
                break;
            }
            case "naprawnicki": {
                if (!Config.instance.funkcje.komendy.naprawnicki) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                if (nickJob != null) {
                    e.reply("Operacja aktualizacji nicków jest już w toku.").complete();
                    return;
                }
                e.reply(new MessageBuilder("Rozpoczęto naprawianie nicków!").setActionRows(ActionRow.of(
                        Button.primary("NICK_PROGRESS", "Sprawdź postęp"),
                        Button.danger("NICK_STOP", "Przerwij")
                )).build()).complete();
                nickJob = new NickJob(e.getGuild(), m -> {
                    String nowyNick = weryfikacja.getNick(m, m.getUser().getName());
                    if (nowyNick.isEmpty()) nowyNick = Config.instance.funkcje.weryfikacja.domyslnyNick + weryfikacja.getSuffix(m);
                    if (nowyNick.length() > 32) nowyNick = nowyNick.substring(0, 32);
                    return nowyNick;
                }, job -> {
                    if (!e.getHook().isExpired()) e.getHook().sendMessage("Gotowe! " + job.getProgress()).complete();
                });
                break;
            }
            case "weryfikacja": {
                if (!Config.instance.funkcje.komendy.weryfikacja) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                if (Weryfikacja.wymuszoneOdblokowanie) {
                    Weryfikacja.wymuszoneOdblokowanie = false;
                    e.reply("Pomyślnie włączono zabezpieczenia weryfikacji!").queue();
                    return;
                }
                Weryfikacja.wymuszoneOdblokowanie = true;
                e.reply("Pomyślnie wyłączono zabezpieczenia weryfikacji!").queue();
                break;
            }
            case "nick": {
                if (!Config.instance.funkcje.weryfikacja.zezwolNaZmianeNicku) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").setEphemeral(true).queue();
                    return;
                }
                e.deferReply(true).queue();
                WeryfikacjaInfo weryfikacja = mbd.getWeryfikacja(e.getUser());
                if (weryfikacja == null) {
                    e.getHook().editOriginal("Nie jesteś zweryfikowany/a!").complete();
                    return;
                }
                if (weryfikacja.isNicknameBlacklist() && !isAdmin(e.getMember())) {
                    e.getHook().editOriginal("Administrator zablokował Ci możliwość zmiany nicku.").complete();
                    return;
                }
                OptionMapping nickOpt = e.getOption("nick");
                String suf = this.weryfikacja.getSuffix(e.getMember());
                String nick;
                if (nickOpt == null || nickOpt.getAsString().isEmpty()) nick = null;
                else {
                    nick = Weryfikacja.fixNick(nickOpt.getAsString());
                    if (!nickOpt.getAsString().equals(nick)) {
                        e.getHook().editOriginal("Podany nick zawiera niedozwolone znaki.").complete();
                        return;
                    }
                    if (nick.length() > (32 - suf.length())) {
                        e.getHook().editOriginal("Podany nick jest za długi.").complete();
                        return;
                    }
                }
                try {
                    e.getMember().modifyNickname(nick == null ? (Weryfikacja.fixNick(e.getUser().getName()) + suf) : (nick + suf)).complete();
                    weryfikacja.setNickname(nick);
                    mbd.save(weryfikacja);
                    if (nick == null) e.getHook().editOriginal("Pomyślnie zresetowano nick.").complete();
                    else e.getHook().editOriginal("Pomyślnie ustawiono twój nick!").complete();
                } catch (Exception ex) {
                    e.getHook().editOriginal("Nie udało się zmienić twojego nicku. Zgłoś się do administracji.").complete();
                    return;
                }
                if (!Config.instance.funkcje.weryfikacja.logi) return;
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(decode("#00ff00"));
                eb.setAuthor("Zmiana nicku");
                eb.setTimestamp(Instant.now());
                eb.setDescription(e.getUser().getAsMention() + " (" + e.getUser().getName()
                        + "#" + e.getUser().getDiscriminator() + ", " + e.getUser().getId() + ") zmienił swój nick!");
                if (nick != null) eb.addField("Nowy nick", nick, false);
                else eb.addField("Nick", "zresetowany", false);
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).queue();
                break;
            }
            case "blacklistnick": {
                if (!Config.instance.funkcje.komendy.blacklistNick) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                Member mem = e.getOption("osoba").getAsMember();
                if (mem == null) {
                    e.reply("Nie znaleziono takiej osoby!").complete();
                    return;
                }
                if (isAdmin(mem)) {
                    e.reply("Nie można wrzucić na blacklistę administratora!").complete();
                    return;
                }
                e.deferReply().allowedMentions(Collections.emptySet()).queue();
                WeryfikacjaInfo weryfikacja = mbd.getWeryfikacja(mem.getUser());
                weryfikacja.setNicknameBlacklist(!weryfikacja.isNicknameBlacklist());
                e.getHook().editOriginal(String.format("Pomyślnie %s możliwość ustawiania nicku osobie %s.",
                        weryfikacja.isNicknameBlacklist() ? "zablokowano" : "odblokowano", mem.getAsMention())).complete();
                mbd.save(weryfikacja);
                if (!Config.instance.funkcje.weryfikacja.logi) return;
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(decode("#00ff00"));
                eb.setAuthor("Blacklista zmiany nicku");
                eb.setTimestamp(Instant.now());
                eb.setDescription(mem.getAsMention() + " (" + mem.getUser().getAsTag() + ", " + mem.getId() + ")");
                eb.addField("Osoba blacklistująca", e.getUser().getAsMention(), false);
                eb.addField("Status blacklisty", weryfikacja.isNicknameBlacklist() ? "dodano" : "usunięto", false);
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).queue();
                break;
            }
            case "ustawnick": {
                if (!Config.instance.funkcje.komendy.ustawNick) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                Member mem = e.getOption("osoba").getAsMember();
                if (mem == null) {
                    e.reply("Nie znaleziono takiej osoby!").complete();
                    return;
                }
                e.deferReply().queue();
                WeryfikacjaInfo werf = mbd.getWeryfikacja(mem.getUser());
                if (werf == null) {
                    e.getHook().editOriginal("Ta osoba nie jest zweryfikowana!").complete();
                    return;
                }
                OptionMapping nickOpt = e.getOption("nick");
                String suf = weryfikacja.getSuffix(mem);
                String nick;
                if (nickOpt == null || nickOpt.getAsString().isEmpty()) nick = null;
                else {
                    nick = nickOpt.getAsString();
                    if (nick.length() > (32 - suf.length())) {
                        e.getHook().editOriginal("Podany nick jest za długi.").complete();
                        return;
                    }
                }
                try {
                    mem.modifyNickname(nick == null ? (Weryfikacja.fixNick(mem.getUser().getName()) + suf) : (nick + suf)).complete();
                    werf.setNickname(nick);
                    mbd.save(werf);
                    if (nick == null) e.getHook().editOriginal("Pomyślnie zresetowano nick.").complete();
                    else e.getHook().editOriginal("Pomyślnie ustawiono nick!").complete();
                } catch (Exception ex) {
                    e.getHook().editOriginal("Nie udało się zmienić nicku tej osoby. Możliwe, że bot nie ma uprawnień.").complete();
                    return;
                }
                if (!Config.instance.funkcje.weryfikacja.logi) return;
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(decode("#00ff00"));
                eb.setAuthor("Zmiana nicku");
                eb.setTimestamp(Instant.now());
                eb.setDescription("Administrator zmienił nick " + mem.getAsMention() + " (" + mem.getUser().getAsTag() + ", " + mem.getId() + ").");
                eb.addField("Zmieniający nick", e.getUser().getAsMention(), false);
                if (nick != null) eb.addField("Nowy nick", nick, false);
                else eb.addField("Nick", "zresetowany", false);
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessage(eb.build()).queue();
                break;
            }
            default: {
                throw new IllegalArgumentException("nieznana nazwa komendy: " + e.getName());
            }
        }
    }

    private boolean isAdmin(Member mem) {
        return mem.getRoles().stream().map(ISnowflake::getId).anyMatch(id -> id.equals(Config.instance.role.admin));
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onButton(ButtonClickEvent e) {
        if (!e.isFromGuild()) return;
        if (e.getComponentId().equals("NICK_PROGRESS")) {
            if (nickJob == null) {
                e.reply("W tej chwili nie trwa żadna aktualizacja nicków!").setEphemeral(true).complete();
                return;
            }
            e.reply(nickJob.getProgress()).setEphemeral(true).complete();
        }
        if (e.getComponentId().equals("NICK_STOP")) {
            if (nickJob == null) {
                e.reply("W tej chwili nie trwa żadna aktualizacja nicków!").setEphemeral(true).complete();
                return;
            }
            if (!isAdmin(e.getMember())) {
                e.reply("Nie masz uprawnień do anulowania tej operacji!").setEphemeral(true).complete();
                return;
            }
            e.deferReply(false).queue();
            nickJob.stop();
            e.getHook().editOriginal("Pomyślnie przerwano aktualizację nicków.").complete();
        }
    }

    private class NickJob implements Runnable {
        private final Guild guild;
        private final Function<Member, String> modifier;
        private final Consumer<NickJob> callback;
        private final Thread thread;

        private final AtomicInteger done = new AtomicInteger();
        private final AtomicInteger errored = new AtomicInteger();
        private volatile int total = -1;

        private NickJob(Guild guild, Function<Member, String> modifier, Consumer<NickJob> callback) {
            this.guild = guild;
            this.modifier = modifier;
            this.callback = callback;
            thread = new Thread(this);
            thread.start();
        }


        @Override
        public void run() {
            try {
                loop(guild.loadMembers().get());
            } finally {
                if (nickJob == this) nickJob = null;
                if (callback != null && !Thread.currentThread().isInterrupted()) callback.accept(this);
            }
        }

        private void loop(List<Member> members) {
            total = members.size();
            for (Member m : members) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    String nowyNick = modifier.apply(m);
                    if (m.getEffectiveName().equals(nowyNick)) {
                        done.getAndAdd(1);
                        continue;
                    }
                    guild.modifyNickname(m, nowyNick).complete();
                    done.getAndAdd(1);
                } catch (Exception ignored) {
                    errored.getAndAdd(1);
                }
            }
        }


        public void stop() {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        public String getProgress() {
            if (total == -1) return "Pobieranie listy osób w toku...";
            return String.format("%s/%s (w tym %s nieudanych)", (done.get() + errored.get()), total, errored.get());
        }
    }
}
