package pl.fratik.FratikDev.funkcje;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.restaction.order.RoleOrderAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.entity.RoleData;
import pl.fratik.FratikDev.entity.SuffixData;
import pl.fratik.FratikDev.entity.WeryfikacjaInfo;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;
import pl.fratik.FratikDev.util.EventWaiter;
import pl.fratik.FratikDev.util.MessageWaiter;
import pl.fratik.FratikDev.util.NetworkUtil;

import java.awt.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.awt.Color.decode;

public class Komendy {
    private static final Logger logger = LoggerFactory.getLogger(Komendy.class);

    private final Weryfikacja weryfikacja;
    private final ManagerBazyDanych mbd;
    private final EventWaiter eventWaiter;
    private NickJob nickJob;

    public Komendy(Weryfikacja weryfikacja, ManagerBazyDanych mbd, JDA jda, EventWaiter eventWaiter) {
        this.weryfikacja = weryfikacja;
        this.mbd = mbd;
        this.eventWaiter = eventWaiter;
        Guild fdev = jda.getGuildById(Config.instance.guildId);
        if (fdev == null) throw new NullPointerException();
        List<CommandData> adminCommands = new ArrayList<>();
        List<SubcommandGroupData> adminSubCommands = new ArrayList<>();
        List<CommandData> boosterCommands = new ArrayList<>();
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
            List<SubcommandData> subs = new ArrayList<>();
            if (Config.instance.funkcje.komendy.ustawNick) {
                subs.add(new SubcommandData("ustaw", "Zmienia nick podanej osoby")
                        .addOption(OptionType.USER, "osoba", "Osoba", true)
                        .addOption(OptionType.STRING, "nick", "Nowy nick (nie wypełniaj by usunąć)", false));
            }
            if (Config.instance.funkcje.komendy.blacklistNick) {
                subs.add(new SubcommandData("blacklist", "Blokuje/odblokowuje możliwość zmiany nicku podanej osobie")
                        .addOption(OptionType.USER, "osoba", "Osoba", true));
            }
            adminSubCommands.add(new SubcommandGroupData("nick", "Zarządzanie nickami").addSubcommands(subs));
        }
        if (Config.instance.funkcje.customRole.wlaczone) {
            Config.CustomRole customRole = Config.instance.funkcje.customRole;
            if (!customRole.zezwolNaZmianeIkony && !customRole.zezwolNaZmianeKoloru) {
                logger.error("CustomRole jest włączone, ale nie ma zezwolenia na zmianę ikony ani koloru: {}", customRole);
            } else {
                String desc;
                if (customRole.zezwolNaZmianeKoloru && customRole.zezwolNaZmianeIkony) desc = "z kolorem i ikoną";
                else if (customRole.zezwolNaZmianeKoloru) desc = "z kolorem";
                else desc = "z ikoną";
                allCommands.add(new CommandData("personalizacja", "Otwiera menu zmiany Twojej roli " + desc + "."));
                List<SubcommandData> subs = new ArrayList<>();
                if (Config.instance.funkcje.komendy.edytujRole) {
                    subs.add(new SubcommandData("edytuj", "Otwiera menu edycji roli podanej osoby")
                            .addOption(OptionType.USER, "osoba", "Osoba", true));
                }
                if (Config.instance.funkcje.komendy.usunRole) {
                    subs.add(new SubcommandData("usun", "Usuwa rolę podanej osobie")
                            .addOption(OptionType.USER, "osoba", "Osoba", true));
                }
                if (Config.instance.funkcje.komendy.blacklistRole) {
                    subs.add(new SubcommandData("blacklist", "Blokuje podanej osobie możliwość personalizowania swojej roli")
                            .addOption(OptionType.USER, "osoba", "Osoba", true));
                }
                adminSubCommands.add(new SubcommandGroupData("personalizacja", "Zarządzanie rolami personalizującymi").addSubcommands(subs));
            }
        }
        adminCommands.add(new CommandData("admin", "Ogólna komenda administracyjna").addSubcommandGroups(adminSubCommands));
        adminCommands.forEach(c -> c.setDefaultEnabled(false));
        if (fdev.getBoostRole() == null) boosterCommands.clear();
        boosterCommands.forEach(c -> c.setDefaultEnabled(false));
        List<Command> commands = fdev.updateCommands().addCommands(adminCommands).addCommands(boosterCommands).addCommands(allCommands).complete();
        commands.stream().filter(c -> adminCommands.stream().anyMatch(d -> d.getName().equals(c.getName())))
                .forEach(c -> c.updatePrivileges(fdev, CommandPrivilege.enableRole(Config.instance.role.admin)).complete());
        commands.stream().filter(c -> boosterCommands.stream().anyMatch(d -> d.getName().equals(c.getName())))
                .forEach(c -> c.updatePrivileges(fdev, CommandPrivilege.enable(fdev.getBoostRole())).complete());
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onCommand(SlashCommandEvent e) {
        switch (e.getCommandPath()) {
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
            case "admin/nick/blacklist": {
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
            case "admin/nick/ustaw": {
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
            case "personalizacja":
            case "admin/personalizacja/edytuj": {
                boolean isAdmin = e.getCommandPath().equals("admin/personalizacja/edytuj");
                if (!Config.instance.funkcje.customRole.wlaczone || (isAdmin && !Config.instance.funkcje.komendy.edytujRole)) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                e.deferReply(true).queue();
                Member member;
                if (isAdmin) member = e.getOption("osoba").getAsMember();
                else member = e.getMember();
                RoleData roleData = mbd.getRoleData(member.getUser());
                if (roleData != null && roleData.isBlacklist()) {
                    e.getHook().editOriginal(isAdmin ? "Ta osoba jest na blackliście." : "Jesteś na blackliście.").queue();
                    return;
                }
                boolean boosted;
                if (!Config.instance.funkcje.customRole.tylkoDlaBoosterow) boosted = false;
                else boosted = e.getGuild().getBoostRole() == null || member.getRoles().contains(e.getGuild().getBoostRole());
                if (roleData == null && (!isAdmin && !boosted)) {
                    e.getHook().editOriginal("Nie masz dostępu do tej funkcji. Boostnij serwer!").queue();
                    return;
                }
                Role role;
                if (roleData != null) role = e.getGuild().getRoleById(roleData.getRoleId());
                else role = null;
                if (role == null) {
                    if (roleData != null) mbd.usunRole(member.getUser());
                    if (!isAdmin && !boosted) {
                        e.getHook().editOriginal("Nie masz dostępu do tej funkcji. Boostnij serwer!").queue();
                        return;
                    }
                    role = e.getGuild().createRole().setName("Kosmetyka - " + member.getEffectiveName()).complete();
                    e.getGuild().addRoleToMember(e.getMember(), role).queue();
                    fixRolePosition(role, e.getMember());
                    mbd.save(roleData = new RoleData(member.getId(), role.getId(), false, isAdmin ? RoleData.Type.UNKNOWN : RoleData.Type.BOOSTER));
                }
                e.getHook().editOriginal(renderRoleEmbed(roleData, role)).queue();
                break;
            }
            case "admin/personalizacja/usun": {
                if (!Config.instance.funkcje.komendy.usunRole) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                Member member = e.getOption("osoba").getAsMember();
                if (member == null) {
                    e.reply("Nie znaleziono takiej osoby.").setEphemeral(true).queue();
                    return;
                }
                e.deferReply().queue();
                RoleData roleData = mbd.getRoleData(member.getUser());
                if (roleData == null) {
                    e.getHook().editOriginal("Nie znaleziono danych roli dla tej osoby.").queue();
                    return;
                }
                if (roleData.isBlacklist()) {
                    e.getHook().editOriginal("Najpierw zdejmij blacklistę.").queue();
                    return;
                }
                mbd.usunRole(member.getUser());
                Role role = e.getGuild().getRoleById(roleData.getRoleId());
                if (role == null) {
                    e.getHook().editOriginal("Nie znaleziono danych roli dla tej osoby.").queue();
                    return;
                }
                try {
                    role.delete().complete();
                } catch (Exception ex) {
                    e.getHook().editOriginal("Nie udało się usunąć roli.").queue();
                    return;
                }
                try {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(decode("#00ff00"));
                    eb.setAuthor("Usunięcie roli kosmetycznej");
                    eb.setTimestamp(Instant.now());
                    eb.setDescription("Usunięto rolę kosmetyczną");
                    eb.addField("Rola", role.getAsMention() + " (" + role.getName() + ", " + role.getId() + ")", true);
                    eb.addField("Usunięta przez", e.getUser().getAsMention() + " (" + e.getUser().getAsTag() + ", " +
                            e.getUser().getId() + ")", true);
                    e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build()).queue();
                } catch (Exception ignored) {}
                e.getHook().editOriginal("Pomyślnie usunięto rolę.").queue();
                break;
            }
            case "admin/personalizacja/blacklist": {
                if (!Config.instance.funkcje.komendy.blacklistRole) {
                    e.reply("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                Member mem = e.getOption("osoba").getAsMember();
                if (mem == null) {
                    e.reply("Nie znaleziono takiej osoby!").complete();
                    return;
                }
                e.deferReply(true).queue();
                RoleData roleData = mbd.getRoleData(mem.getUser());
                if (roleData != null && roleData.isBlacklist()) {
                    mbd.usunRole(mem.getUser());
                    try {
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setColor(decode("#00ff00"));
                        eb.setAuthor("Blacklista personalizacji");
                        eb.setTimestamp(Instant.now());
                        eb.setDescription("Usunięto użytkownika z blacklisty personalizacji");
                        eb.addField("Użytkownik", mem.getAsMention() + " (" + mem.getUser().getAsTag() + ", " + mem.getId() + ")", true);
                        eb.addField("Usunięty przez", e.getUser().getAsMention() + " (" + e.getUser().getAsTag() + ", " +
                                e.getUser().getId() + ")", true);
                        e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build()).queue();
                    } catch (Exception ignored) {}
                    e.getHook().editOriginal("Pomyślnie usunięto użytkownika z blacklisty!").queue();
                    return;
                }
                if (isAdmin(mem)) {
                    e.getHook().editOriginal("Nie można wrzucić na blacklistę administratora!").queue();
                    return;
                }
                if (roleData != null) {
                    mbd.usunRole(mem.getUser());
                    Role role = e.getGuild().getRoleById(roleData.getRoleId());
                    if (role != null) role.delete().queue();
                }
                mbd.save(new RoleData(mem.getId(), null, true, RoleData.Type.UNKNOWN));
                try {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(decode("#00ff00"));
                    eb.setAuthor("Blacklista personalizacji");
                    eb.setTimestamp(Instant.now());
                    eb.setDescription("Dodano użytkownika na blacklistę personalizacji");
                    eb.addField("Użytkownik", mem.getAsMention() + " (" + mem.getUser().getAsTag() + ", " + mem.getId() + ")", true);
                    eb.addField("Dodany przez", e.getUser().getAsMention() + " (" + e.getUser().getAsTag() + ", " +
                            e.getUser().getId() + ")", true);
                    e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build()).queue();
                } catch (Exception ignored) {}
                e.getHook().editOriginal("Pomyślnie dodano użytkownika na blacklistę!").queue();
                break;
            }
            default: {
                throw new IllegalArgumentException("nieznana nazwa komendy: " + e.getName());
            }
        }
    }

    private void fixRolePosition(Role role, Member member) {
        Role memberHighestRole = null; // null jeżeli brak (@everyone) lub role == memberHighestRole (wtedy ustaw pos na 0)
        List<String> allRoleIds = mbd.getAllRoleData().stream().map(RoleData::getRoleId).collect(Collectors.toList());
        if (!member.getRoles().isEmpty() && !(member.getRoles().size() == 1 && member.getRoles().get(0).equals(role))) {
            // jeżeli lista ról nie jest pusta i jedyną rolą użytkownika nie jest rola kosmetyczna
            List<Role> roles = new ArrayList<>();
            for (Role guildRole : member.getGuild().getRoles()) {
                // filtrujemy role — bierzemy tylko naszą rolę kosmetyczną i wszystkie nie-kosmetyczne
                if (guildRole.equals(role) || !allRoleIds.contains(guildRole.getId())) roles.add(guildRole);
            }
            for (Role memberRole : member.getRoles()) {
                if (!memberRole.equals(role)) {
                    // zapisujemy najwyższą rolę członka jako rolę pod kosmetyczną
                    // to nigdy nie może być null, bo wyżej sprawdzamy czy jedyną rolą przypadkiem nie jest rola kosmetyczna
                    memberHighestRole = memberRole;
                    break;
                }
            }
            if (memberHighestRole == null) throw new IllegalStateException("jeżeli to jest w logach to coś poważnie wyjebało");
            if (roles.indexOf(role) + 1 == roles.indexOf(memberHighestRole)) {
                // nie licząc ról innych kosmetycznych, nasza kosmetyczna jest centralnie nad naszą najwyższą rolą,
                // czyli nic nie trzeba więcej modyfikować
                return;
            }
        } else {
            // tu trzeba sprawdzić, czy nasza cudowna kosmetyczna jest nad @eve
            List<Role> roles = new ArrayList<>();
            for (Role guildRole : member.getGuild().getRoles()) {
                // filtrujemy role — bierzemy tylko naszą rolę kosmetyczną i wszystkie nie-kosmetyczne
                if (guildRole.equals(role) || !allRoleIds.contains(guildRole.getId())) roles.add(guildRole);
            }
            if (roles.indexOf(role) + 1 == roles.size() - 1) {
                // nie licząc ról innych kosmetycznych, nasza kosmetyczna jest centralnie nad @everyone,
                // czyli nic nie trzeba więcej modyfikować
                return;
            }
        }
        RoleOrderAction orderAction = role.getGuild().modifyRolePositions(true).selectPosition(role);
        orderAction.moveTo(Math.min(orderAction.getCurrentOrder().indexOf(role.getGuild().getSelfMember().getRoles().get(0)) - 1,
                memberHighestRole == null ? 0 : (orderAction.getCurrentOrder().indexOf(memberHighestRole) + 1))).queue();
    }

    private Message renderRoleEmbed(RoleData roleData, Role role) {
        String za;
        if (roleData.getType() == RoleData.Type.BOOSTER) za = " za boost serwera";
        else if (roleData.getType() == RoleData.Type.REWARD) za = " jako nagrodę";
        else za = "";
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(role.getColor())
                .setTitle("Zarządzanie rolą kosmetyczną")
                .setDescription("Nazwa: " + role.getName() + "\nOtrzymano rolę " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                        .format(Date.from(role.getTimeCreated().toInstant())) + za + ".")
                .setFooter(role.getId());
        Boolean changeRoleIcon; // true - zmień, false - dodaj, null - brak możliwości
        Boolean changeRoleColor; // true - zmień, false - dodaj, null - brak możliwości
        if (Config.instance.funkcje.customRole.zezwolNaZmianeIkony) {
            if (role.getGuild().getFeatures().contains("ROLE_ICONS")) {
                if (role.getIconId() != null) {
                    eb.addField("Ikona roli", "jest wyświetlona w embedzie", true);
                    eb.setThumbnail(role.getIconUrl());
                    changeRoleIcon = true;
                } else if (role.getEmoji() != null) {
                    eb.addField("Ikona roli", role.getEmoji(), true);
                    changeRoleIcon = true;
                } else {
                    eb.addField("Ikona roli", "nie została ustawiona", true);
                    changeRoleIcon = false;
                }
            } else {
                eb.addField("Ikona roli", "Serwer nie ma dostępu do ikon. Prawdopodobnie brakuje boostów.", false);
                changeRoleIcon = null;
            }
        } else changeRoleIcon = null;
        if (Config.instance.funkcje.customRole.zezwolNaZmianeKoloru) {
            if (role.getColor() == null) {
                eb.addField("Kolor", "*brak*", true);
                changeRoleColor = false;
            } else {
                eb.addField("Kolor", "#" + String.format("%06x", role.getColorRaw()), true);
                changeRoleColor = true;
            }
        } else {
            if (changeRoleIcon == null)
                return new MessageBuilder("Obecnie nie możesz zmienić żadnej opcji w swojej roli kosmetycznej.").build();
            changeRoleColor = null;
        }
        eb.addField("Ostrzeżenie!", "Wciśnięcie przycisku spowoduje wysłanie widocznej dla wszystkich " +
                "wiadomości na chacie. Upewnij się, że jesteś na kanale gdzie komendy są dozwolone.", false);
        MessageBuilder mb = new MessageBuilder(eb.build());
        List<Component> components = new ArrayList<>();
        List<Component> components2 = new ArrayList<>();
        if (changeRoleIcon != null) {
            if (changeRoleIcon) { //NOSONAR
                components.add(Button.primary("SET_ICON", "Zmień ikonę"));
                components2.add(Button.danger("DELETE_ICON", "Usuń ikonę"));
            } else components.add(Button.primary("SET_ICON", "Ustaw ikonę"));
        }
        if (changeRoleColor != null) {
            if (changeRoleColor) { //NOSONAR
                components.add(Button.primary("SET_COLOR", "Zmień kolor"));
                components2.add(Button.danger("DELETE_COLOR", "Usuń kolor"));
            } else components.add(Button.primary("SET_COLOR", "Ustaw kolor"));
        }
        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(components));
        if (!components2.isEmpty()) rows.add(ActionRow.of(components2));
        mb.setActionRows(rows);
        return mb.build();
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onUnboost(GuildMemberUpdateBoostTimeEvent e) {
        if (e.getNewTimeBoosted() != null) return;
        RoleData roleData = mbd.getRoleData(e.getUser());
        if (roleData == null || roleData.isBlacklist()) return;
        if (roleData.getType() == RoleData.Type.BOOSTER) {
            Role role = e.getGuild().getRoleById(roleData.getRoleId());
            if (role != null) role.delete().queue();
            mbd.usunRole(e.getUser());
        }
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onLeave(GuildMemberRemoveEvent e) {
        RoleData roleData = mbd.getRoleData(e.getUser());
        if (roleData == null || roleData.isBlacklist()) return;
        if (roleData.getType() == RoleData.Type.BOOSTER) {
            Role role = e.getGuild().getRoleById(roleData.getRoleId());
            if (role != null) role.delete().queue();
            mbd.usunRole(e.getUser());
        }
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onJoin(GuildMemberJoinEvent e) {
        RoleData roleData = mbd.getRoleData(e.getUser());
        if (roleData == null || roleData.isBlacklist()) return;
        if (roleData.getType() != RoleData.Type.BOOSTER) {
            Role role = e.getGuild().getRoleById(roleData.getRoleId());
            if (role == null) return;
            e.getGuild().addRoleToMember(e.getMember(), role).queue();
            fixRolePosition(role, e.getMember());
        }
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onRolePosChange(RoleUpdatePositionEvent e) {
        if (e.getOldPosition() == e.getNewPosition()) return;
        if (!mbd.getRoleDataByRole(e.getRole()).isEmpty()) return;
        for (Member member : e.getGuild().getMembersWithRoles(e.getRole())) {
            RoleData roleData = mbd.getRoleData(member.getUser());
            if (roleData == null || roleData.isBlacklist()) return;
            Role role = e.getGuild().getRoleById(roleData.getRoleId());
            if (role != null) fixRolePosition(role, member);
        }
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onRoleAdd(GuildMemberRoleAddEvent e) {
        RoleData roleData = mbd.getRoleData(e.getMember().getUser());
        if (roleData == null || roleData.isBlacklist()) return;
        Role role = e.getGuild().getRoleById(roleData.getRoleId());
        if (role != null) fixRolePosition(role, e.getMember());
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onRoleRemove(GuildMemberRoleRemoveEvent e) {
        RoleData roleData = mbd.getRoleData(e.getMember().getUser());
        if (roleData == null || roleData.isBlacklist()) return;
        Role role = e.getGuild().getRoleById(roleData.getRoleId());
        if (role != null) fixRolePosition(role, e.getMember());
    }

    @Subscribe
    @AllowConcurrentEvents
    public void onButtonClick(@NotNull ButtonClickEvent e) {
        if (!e.isFromGuild() || !e.getGuild().getId().equals(Config.instance.guildId)) return;
        String roleId;
        if (e.getMessage().getEmbeds().size() != 1) return;
        MessageEmbed.Footer f = e.getMessage().getEmbeds().get(0).getFooter();
        if (f == null) return;
        roleId = f.getText();
        if (roleId == null) return;
        Role role;
        try {
            role = e.getGuild().getRoleById(roleId);
        } catch (Exception ex) {
            return;
        }
        if (role == null) return;
        if (e.getComponentId().equals("SET_ICON") && Config.instance.funkcje.customRole.zezwolNaZmianeIkony) {
            MessageWaiter waiter = new MessageWaiter(eventWaiter, new MessageWaiter.Context(e.getUser(), e.getChannel()));
            e.deferReply().queue();
            waiter.setMessageHandler(m -> {
                if (m.getMessage().getAttachments().isEmpty()) {
                    try {
                        //todo lepsza detekcja czy emotka XD
                        if (m.getMessage().getContentRaw().length() > 4) throw new Exception("raczej nie emotka");
                        role.getManager().setEmoji(m.getMessage().getContentRaw()).complete();
                    } catch (Exception ex) {
                        m.getMessage().reply("Twoja wiadomość nie zawiera załączników ani prawidłowej emotki.").queue();
                        return;
                    }
                    try {
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setColor(decode("#00ff00"));
                        eb.setAuthor("Zmiana ikony roli");
                        eb.setTimestamp(Instant.now());
                        eb.setDescription("Ustawiono ikonę roli");
                        eb.addField("Rola", role.getAsMention() + " (" + role.getName() + ", " + role.getId() + ")", true);
                        eb.addField("Nowa ikona", m.getMessage().getContentRaw(), true);
                        eb.addField("Ustawiona przez", m.getAuthor().getAsMention() + " (" + m.getAuthor().getAsTag() + ", " +
                                m.getAuthor().getId() + ")", true);
                        e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build()).queue();
                    } catch (Exception ignored) {}
                    m.getMessage().reply("Pomyślnie ustawiono emotkę roli.").queue();
                } else {
                    Message.Attachment attachment = m.getMessage().getAttachments().get(0);
                    if (attachment.getSize() > 262144) {
                        m.getMessage().reply("Za duży plik. Maksymalny rozmiar to 256KB.").queue();
                        return;
                    }
                    Icon.IconType type;
                    if (attachment.getFileExtension() == null) type = null;
                    else if (attachment.getFileExtension().equals("jpg")) type = Icon.IconType.JPEG;
                    else if (attachment.getFileExtension().equals("jpeg")) type = Icon.IconType.JPEG;
                    else if (attachment.getFileExtension().equals("png")) type = Icon.IconType.PNG;
                    else type = null;
                    if (type == null) {
                        m.getMessage().reply("Nie rozpoznano formatu. Musisz wysłać plik w formacie JPEG lub PNG.").queue();
                        return;
                    }
                    byte[] bytes;
                    try {
                        bytes = NetworkUtil.download(attachment.getUrl());
                    } catch (IOException ex) {
                        logger.error("Pobieranko wyjebało", ex);
                        m.getMessage().reply("Nie udało się pobrać załącznika. Spróbuj ponownie później.").queue();
                        return;
                    }
                    TextChannel weryfChan = Config.instance.funkcje.customRole.getWeryfikacjaAdministracyjna(e.getJDA());
                    if (weryfChan != null) {
                        try {
                            EmbedBuilder eb = new EmbedBuilder()
                                    .setTitle("Weryfikacja ikony roli")
                                    .setAuthor(m.getAuthor().getAsTag(), null, m.getAuthor().getEffectiveAvatarUrl())
                                    .setImage("attachment://ikona." + attachment.getFileExtension())
                                    .setFooter(role.getId());
                            weryfChan.sendMessage(new MessageBuilder(eb.build())
                                            .setActionRows(ActionRow.of(
                                                    Button.success("ICON_ACCEPT", "Zatwierdź"),
                                                    Button.danger("ICON_REJECT", "Odrzuć")
                                            )).build()
                                    ).addFile(bytes, "ikona." + attachment.getFileExtension())
                                    .complete();
                        } catch (ErrorResponseException ex) {
                            m.getMessage().reply("Nie udało się wysłać ikony na kanał administracyjny. Zgłoś to administracji serwera.").queue();
                            return;
                        }
                        m.getMessage().reply("Załącznik został wysłany na kanał administracyjny w celu weryfikacji. " +
                                "Po pomyślnej weryfikacji, Twoja rola zmieni ikonę.").queue();
                        return;
                    }
                    try {
                        role.getManager().setIcon(Icon.from(bytes, type)).complete();
                    } catch (Exception ex) {
                        logger.error("Nie udało się ustawić ikony", ex);
                        m.getMessage().reply("Nie udało się ustawić ikony.").queue();
                        return;
                    }
                    try {
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setColor(decode("#00ff00"));
                        eb.setAuthor("Zmiana ikony roli");
                        eb.setTimestamp(Instant.now());
                        eb.setDescription("Ustawiono ikonę roli");
                        eb.setImage("attachment://ikona." + attachment.getFileExtension());
                        eb.addField("Rola", role.getAsMention() + " (" + role.getName() + ", " + role.getId() + ")", true);
                        eb.addField("Nowa ikona", "została wstawiona jako załącznik do embeda", true);
                        eb.addField("Ustawiona przez", m.getAuthor().getAsMention() + " (" + m.getAuthor().getAsTag() + ", " +
                                m.getAuthor().getId() + ")\nIkona nie została wysłana do zatwierdzenia.", true);
                        e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build())
                                .addFile(bytes, "ikona." + attachment.getFileExtension()).queue();
                    } catch (Exception ignored) {}
                    m.getMessage().reply("Pomyślnie zmieniono ikonę.").complete();
                }
            });
            waiter.setTimeoutHandler(() -> e.getHook().editOriginal(e.getUser().getAsMention() + ": czas minął.").queue());
            e.getHook().editOriginal(e.getUser().getAsMention() + ": wyślij zdjęcie lub emotkę unicode by zmienić ikonę swojej roli.").complete();
            waiter.create();
        }
        if (e.getComponentId().equals("DELETE_ICON") && Config.instance.funkcje.customRole.zezwolNaZmianeIkony) {
            e.deferReply().queue();
            role.getManager().setIcon(null).setEmoji(null).complete();
            try {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(decode("#00ff00"));
                eb.setAuthor("Zmiana ikony roli");
                eb.setTimestamp(Instant.now());
                eb.setDescription("Usunięto ikonę roli");
                eb.addField("Rola", role.getAsMention() + " (" + role.getName() + ", " + role.getId() + ")", true);
                eb.addField("Usunięta przez", e.getUser().getAsMention() + " (" + e.getUser().getAsTag() + ", " +
                        e.getUser().getId() + ")", true);
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build()).queue();
            } catch (Exception ignored) {}
            e.getHook().editOriginal(e.getUser().getAsMention() + ": pomyślnie usunięto ikonę.").queue();
        }
        if (e.getComponentId().equals("ICON_ACCEPT") && e.getChannel().equals(Config.instance.funkcje.customRole
                .getWeryfikacjaAdministracyjna(e.getJDA()))) {
            if (e.getMessage().getEmbeds().size() != 1) return;
            MessageEmbed embed = e.getMessage().getEmbeds().get(0);
            if (embed.getImage() == null || embed.getImage().getUrl() == null) return;
            Icon.IconType type;
            if (embed.getImage().getUrl().endsWith("ikona.jpg") || embed.getImage().getUrl().endsWith("ikona.jpeg")) type = Icon.IconType.JPEG;
            else if (embed.getImage().getUrl().endsWith("ikona.png")) type = Icon.IconType.PNG;
            else return;
            e.deferReply(true).queue();
            byte[] bytes;
            try {
                bytes = NetworkUtil.download(embed.getImage().getUrl());
            } catch (IOException ex) {
                logger.error("Pobieranko wyjebało", ex);
                e.getHook().editOriginal("Nie udało się pobrać załącznika. Spróbuj ponownie później.").queue();
                return;
            }
            try {
                role.getManager().setIcon(Icon.from(bytes, type)).complete();
            } catch (Exception ex) {
                logger.error("Nie udało się ustawić ikony", ex);
                e.getHook().editOriginal("Nie udało się ustawić ikony.").queue();
                return;
            }
            e.getMessage().delete().queue();
            try {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(decode("#00ff00"));
                eb.setAuthor("Zmiana ikony roli");
                eb.setTimestamp(Instant.now());
                eb.setDescription("Ustawiono ikonę roli");
                String ext = type == Icon.IconType.PNG ? "png" : "jpg";
                eb.setImage("attachment://ikona." + ext);
                eb.addField("Rola", role.getAsMention() + " (" + role.getName() + ", " + role.getId() + ")", true);
                eb.addField("Nowa ikona", "została wstawiona jako załącznik do embeda", true);
                eb.addField("Zatwierdzona przez", e.getUser().getAsMention() + " (" + e.getUser().getAsTag() + ", " +
                        e.getUser().getId() + ")", true);
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build())
                        .addFile(bytes, "ikona." + ext).queue();
            } catch (Exception ignored) {}
            e.getHook().editOriginal("Pomyślnie zmieniono ikonę.").complete();
        }
        if (e.getComponentId().equals("ICON_REJECT") && e.getChannel().equals(Config.instance.funkcje.customRole
                .getWeryfikacjaAdministracyjna(e.getJDA()))) {
            if (e.getMessage().getEmbeds().size() != 1) return;
            MessageEmbed embed = e.getMessage().getEmbeds().get(0);
            if (embed.getImage() == null || embed.getImage().getUrl() == null) return;
            e.deferReply(true).queue();
            e.getMessage().delete().queue();
            e.getHook().editOriginal("Pomyślnie odrzucono zmianę ikony.").queue();
        }
        if (e.getComponentId().equals("SET_COLOR") && Config.instance.funkcje.customRole.zezwolNaZmianeKoloru) {
            MessageWaiter waiter = new MessageWaiter(eventWaiter, new MessageWaiter.Context(e.getUser(), e.getChannel()));
            e.deferReply().queue();
            waiter.setMessageHandler(m -> {
                if (!m.getMessage().getContentRaw().toLowerCase().matches("^#[0-9a-f]{6}$")) {
                    m.getMessage().reply("Nieprawidłowy format koloru.").queue();
                    return;
                }
                Color color = Color.decode(m.getMessage().getContentRaw());
                role.getManager().setColor(color).complete();
                try {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(decode("#00ff00"));
                    eb.setAuthor("Zmiana koloru roli");
                    eb.setTimestamp(Instant.now());
                    eb.setDescription("Zmieniono kolor roli kosmetycznej");
                    eb.addField("Rola", role.getAsMention() + " (" + role.getName() + ", " + role.getId() + ")", true);
                    eb.addField("Nowy kolor", "#" + String.format("%06x", color.getRGB() & 0xFFFFFF), true);
                    eb.addField("Zmieniono przez", m.getAuthor().getAsMention() + " (" + m.getAuthor().getAsTag() + ", " +
                            m.getAuthor().getId() + ")", true);
                    e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build()).queue();
                } catch (Exception ignored) {}
                m.getMessage().reply("Pomyślnie zmieniono kolor.").complete();
            });
            waiter.setTimeoutHandler(() -> e.getHook().editOriginal(e.getUser().getAsMention() + ": czas minął.").queue());
            e.getHook().editOriginal(e.getUser().getAsMention() + ": wyślij kolor w formacie #RRGGBB.").complete();
            waiter.create();
        }
        if (e.getComponentId().equals("DELETE_COLOR") && Config.instance.funkcje.customRole.zezwolNaZmianeKoloru) {
            e.deferReply().queue();
            role.getManager().setColor(null).complete();
            try {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(decode("#00ff00"));
                eb.setAuthor("Zmiana koloru roli");
                eb.setTimestamp(Instant.now());
                eb.setDescription("Zresetowano kolor roli kosmetycznej");
                eb.addField("Rola", role.getAsMention() + " (" + role.getName() + ", " + role.getId() + ")", true);
                eb.addField("Nowy kolor", "*brak*", true);
                eb.addField("Usunięto przez", e.getUser().getAsMention() + " (" + e.getUser().getAsTag() + ", " +
                        e.getUser().getId() + ")", true);
                e.getJDA().getTextChannelById(Config.instance.kanaly.logiWeryfikacji).sendMessageEmbeds(eb.build()).queue();
            } catch (Exception ignored) {}
            e.getHook().editOriginal(e.getUser().getAsMention() + ": pomyślnie usunięto kolor.").queue();
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
