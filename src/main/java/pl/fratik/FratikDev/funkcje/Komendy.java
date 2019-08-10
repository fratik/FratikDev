package pl.fratik.FratikDev.funkcje;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import pl.fratik.FratikDev.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Komendy {
    @Subscribe
    @AllowConcurrentEvents
    public void onMessage(MessageReceivedEvent e) {
        if (e.getAuthor().isBot()) return;
        Message msg = e.getMessage();
        if (!msg.isMentioned(e.getJDA().getSelfUser())) return;
        if (!e.getGuild().getId().equals(Config.instance.guildId)) return;
        if (!msg.getContentRaw().replaceAll("<@!", "<@").startsWith(e.getJDA().getSelfUser().getAsMention() + ", ")) {
            e.getChannel().sendMessage(String.format("Co ja? [%s, help]", e.getJDA().getSelfUser().getAsMention())).queue();
            return;
        }
        String content = e.getMessage().getContentRaw().replaceAll("<@!", "<@").substring((e.getJDA().getSelfUser().getAsMention() + ", ").length());
        if (content.isEmpty()) return;
        List<String> _args = new ArrayList<>(Arrays.asList(content.split(" ")));
        String command = _args.get(0);
        _args.remove(0);
        String[] args = _args.toArray(new String[0]);
        if (e.getMember().getRoles().stream().map(ISnowflake::getId).noneMatch(id -> id.equals(Config.instance.role.admin))) {
            e.getChannel().sendMessage("Równouprawnienie to stan umysłu [ten bot jest zarezerwowany tylko dla administratorów!]").complete();
            return;
        }
        switch (command) {
            case "help":
            case "hlep":
            case "pomusz":
            case "pomoc":
            case "komendy": {
                msg.getChannel().sendMessage("FratikDev " + getClass().getPackage().getImplementationVersion() +
                        "\n\nDostępne komendy:\nhelp - To coś\nsuffix <tekst> - Dodaje tekst do nicku każdej osoby na serwerze" +
                        "\nusunsuffix <tekst> - To samo co wyżej, tylko usuwa tekst\nnaprawnicki - Poprawia rakowe nicki" +
                        "\nweryfikacja - Przełącza zabezpieczenia weryfikacji").queue();
                break;
            }
            case "suffix":
            case "ponicku": {
                if (!Config.instance.funkcje.komendy.suffix) {
                    msg.getChannel().sendMessage("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                String suffix = " " + String.join(" ", args);
                if (suffix.isEmpty()) {
                    e.getChannel().sendMessage("Suffix nie może być pusty!").queue();
                    return;
                }
                Message mes = e.getChannel().sendMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                        .getAsMention() + " Pracuję...").complete();
                AtomicInteger done = new AtomicInteger();
                AtomicInteger errors = new AtomicInteger();
                new Thread(() -> {
                    for (Member m : e.getGuild().getMembers()) {
                        try {
                            if (m.getEffectiveName().endsWith(suffix)) {
                                done.getAndAdd(1);
                                continue;
                            }
                            e.getGuild().getController().setNickname(m, m.getEffectiveName() + suffix).complete();
                            done.getAndAdd(1);
                        } catch (Exception ignored) {
                            errors.getAndAdd(1);
                        }
                    }
                }).start();
                while (done.get() + errors.get() != e.getGuild().getMembers().size()) {
                    mes.editMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                            .getAsMention() + " Pracuję... " +
                            String.format("%s/%s (w tym %s nieudanych)", String.valueOf(done.get() + errors.get()),
                                    String.valueOf(e.getGuild().getMembers().size()), String.valueOf(errors.get())))
                            .complete();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
                mes.editMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                        .getAsMention() + " Pracuję... " +
                        String.format("%s/%s (w tym %s nieudanych)", String.valueOf(done.get() + errors.get()),
                                String.valueOf(e.getGuild().getMembers().size()), String.valueOf(errors.get())))
                        .complete();
                e.getChannel().sendMessage("Gotowe!").queue();
                break;
            }
            case "usunsuffix": {
                if (!Config.instance.funkcje.komendy.suffix) {
                    msg.getChannel().sendMessage("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                String suffix = " " + String.join(" ", args);
                if (suffix.isEmpty()) {
                    e.getChannel().sendMessage("Suffix nie może być pusty!").queue();
                    return;
                }
                Message mes = e.getChannel().sendMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                        .getAsMention() + " Pracuję...").complete();
                AtomicInteger done = new AtomicInteger();
                AtomicInteger errors = new AtomicInteger();
                new Thread(() -> {
                    for (Member m : e.getGuild().getMembers()) {
                        try {
                            if (!m.getEffectiveName().endsWith(suffix)) continue;
                            e.getGuild().getController().setNickname(m, m.getEffectiveName().replaceAll(suffix, "")).complete();
                            done.getAndAdd(1);
                        } catch (Exception ignored) {
                            errors.getAndAdd(1);
                        }
                    }
                }).start();
                while (done.get() + errors.get() != e.getGuild().getMembers().stream().filter(m -> m.getEffectiveName().endsWith(suffix)).count()) {
                    mes.editMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                            .getAsMention() + " Pracuję... " +
                            String.format("%s/%s (w tym %s nieudanych)", String.valueOf(done.get() + errors.get()),
                                    String.valueOf(e.getGuild().getMembers().size()), String.valueOf(errors.get())))
                            .complete();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                mes.editMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                        .getAsMention() + " Pracuję... " +
                        String.format("%s/%s (w tym %s nieudanych)", String.valueOf(done.get() + errors.get()),
                                String.valueOf(e.getGuild().getMembers().stream().filter(m -> m.getEffectiveName().endsWith(suffix)).count())
                                , String.valueOf(errors.get())))
                        .complete();
                e.getChannel().sendMessage("Gotowe!").queue();
                break;
            }
            case "naprawnicki": {
                if (!Config.instance.funkcje.komendy.naprawnicki) {
                    msg.getChannel().sendMessage("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                Message mes = e.getChannel().sendMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                        .getAsMention() + " Pracuję...").complete();
                AtomicInteger done = new AtomicInteger();
                AtomicInteger errors = new AtomicInteger();
                new Thread(() -> {
                    for (Member m : e.getGuild().getMembers()) {
                        try {
                            String nowyNick = m.getUser().getName().replaceAll("[^A-Za-z0-9 ĄąĆćĘęŁłŃńÓóŚśŹźŻż./\\\\!@#$%^&*()_+\\-=\\[\\]';<>?,~`{}|\":]", "");
                            Matcher matcher = Pattern.compile("^([^A-Za-z0-9ĄąĆćĘęŁłŃńÓóŚśŹźŻż]+)").matcher(nowyNick);
                            boolean oho = matcher.find();
                            if (oho) nowyNick = nowyNick.replaceFirst(Pattern.quote(matcher.group(1)), "");
                            if (nowyNick.isEmpty()) nowyNick = "mam rakowy nick";
                            if (m.getEffectiveName().equals(nowyNick)) {
                                done.getAndAdd(1);
                                continue;
                            }
                            e.getGuild().getController().setNickname(m, nowyNick).complete();
                            done.getAndAdd(1);
                        } catch (Exception ignored) {
                            errors.getAndAdd(1);
                        }
                    }
                }).start();
                while (done.get() + errors.get() != e.getGuild().getMembers().size()) {
                    mes.editMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                            .getAsMention() + " Pracuję... " +
                            String.format("%s/%s (w tym %s nieudanych)", String.valueOf(done.get() + errors.get()),
                                    String.valueOf(e.getGuild().getMembers().size()), String.valueOf(errors.get())))
                            .complete();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                mes.editMessage(e.getJDA().getEmoteById(Config.instance.emotki.loading)
                        .getAsMention() + " Pracuję... " +
                        String.format("%s/%s (w tym %s nieudanych)", String.valueOf(done.get() + errors.get()),
                                String.valueOf(e.getGuild().getMembers().size()), String.valueOf(errors.get())))
                        .complete();
                e.getChannel().sendMessage("Gotowe!").queue();
                break;
            }
            case "weryfikacja": {
                if (!Config.instance.funkcje.komendy.weryfikacja) {
                    msg.getChannel().sendMessage("Funkcja została wyłączona w konfiguracji bota.").queue();
                    return;
                }
                if (Weryfikacja.wymuszoneOdblokowanie) {
                    Weryfikacja.wymuszoneOdblokowanie = false;
                    e.getChannel().sendMessage("Pomyślnie włączono zabezpieczenia weryfikacji!").queue();
                    return;
                }
                Weryfikacja.wymuszoneOdblokowanie = true;
                e.getChannel().sendMessage("Pomyślnie wyłączono zabezpieczenia weryfikacji!").queue();
            }
        }
    }
}
