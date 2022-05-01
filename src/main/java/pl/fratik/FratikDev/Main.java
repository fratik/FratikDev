package pl.fratik.FratikDev;

import com.google.common.eventbus.AsyncEventBus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.funkcje.Komendy;
import pl.fratik.FratikDev.funkcje.Urlopy;
import pl.fratik.FratikDev.funkcje.Weryfikacja;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;
import pl.fratik.FratikDev.manager.implementation.ManagerBazyDanychImpl;
import pl.fratik.FratikDev.util.EventWaiter;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public class Main {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main(String token) throws InterruptedException, LoginException {
        Config config = new Config();
        AsyncEventBus eventBus = new AsyncEventBus(Executors.newFixedThreadPool(4));
        File cfg = new File("config.json");
        if (!cfg.exists()) {
            try {
                if (cfg.createNewFile()) {
                    config = new Config();
                    config.funkcje.customRole.weryfikacjaAdministracyjna = "<ID kanału>";

                    Files.write(cfg.toPath(), GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
                    logger.info("Konfiguracja stworzona, skonfiguruj bota!");
                    System.exit(1);
                }
            } catch (Exception e) {
                logger.error("Nie udało się stworzyć konfiguracji!", e);
                System.exit(1);
            }
        }
        try {
            config = GSON.fromJson(new FileReader(cfg), Config.class);
        } catch (Exception e) {
            logger.error("Nie udało się odczytać konfiguracji!", e);
            System.exit(1);
        }
        Config.instance = config;
        JDA jda = JDABuilder.createDefault(token)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new JDAEventHandler(eventBus)).setEnableShutdownHook(false)
                .setStatus(OnlineStatus.ONLINE).setActivity(Activity.watching("FratikDev")).build();
        jda.awaitReady();
        ManagerBazyDanych managerBazyDanych = new ManagerBazyDanychImpl();
        managerBazyDanych.load();
        EventWaiter eventWaiter = new EventWaiter(Executors.newSingleThreadScheduledExecutor(), false);
        Urlopy urlopy;
        if (Config.instance.funkcje.urlopy) urlopy = new Urlopy(managerBazyDanych, jda);
        else urlopy = null;
        Weryfikacja weryfikacja;
        if (Config.instance.funkcje.weryfikacja.wlaczone) weryfikacja = new Weryfikacja(managerBazyDanych, jda);
        else weryfikacja = null;
        Komendy komendy;
        if (Config.instance.funkcje.komendy.wlaczone) komendy = new Komendy(weryfikacja, managerBazyDanych, jda, eventWaiter);
        else komendy = null;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            eventWaiter.shutdown();
            if (urlopy != null) eventBus.unregister(urlopy);
            if (weryfikacja != null) eventBus.unregister(weryfikacja);
            if (komendy != null) eventBus.unregister(komendy);
            managerBazyDanych.shutdown();
            jda.shutdownNow();
        }));
        eventBus.register(eventWaiter);
        if (urlopy != null) eventBus.register(urlopy);
        if (weryfikacja != null) eventBus.register(weryfikacja);
        if (komendy != null) eventBus.register(komendy);
    }

    public static void main(String[] args) {
        try {
            logger.info("Ładowanie...");
            if (args.length == 0) {
                logger.error("Nie podano tokenu!");
                return;
            }
            new Main(args[0]);
        } catch (Exception e) {
            logger.error("Wystąpił błąd!", e);
            System.exit(2);
        }
    }

}
