package pl.fratik.FratikDev;

import com.google.common.eventbus.EventBus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main(String token) throws InterruptedException, LoginException {
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Config config = new Config();
        EventBus eventBus = new EventBus("glowny");
        File cfg = new File("config.json");
        if (!cfg.exists()) {
            try {
                if (cfg.createNewFile()) {
                    config = new Config();

                    Files.write(cfg.toPath(), gson.toJson(config).getBytes(StandardCharsets.UTF_8));
                    logger.info("Konfiguracja stworzona, skonfiguruj bota!");
                    System.exit(1);
                }
            } catch (Exception e) {
                logger.error("Nie udało się stworzyć konfiguracji!", e);
                System.exit(1);
            }
        }
        try {
            config = gson.fromJson(new FileReader(cfg), Config.class);
        } catch (Exception e) {
            logger.error("Nie udało się odczytać konfiguracji!", e);
            System.exit(1);
        }
        Config.instance = config;
        JDA jda = new JDABuilder(token).addEventListener(new JDAEventHandler(eventBus)).setEnableShutdownHook(false)
                .setStatus(OnlineStatus.ONLINE).setGame(Game.watching("FratikDev")).build();
        jda.awaitReady();
        ManagerBazyDanych managerBazyDanych = new ManagerBazyDanychImpl();
        managerBazyDanych.load();
        EventWaiter eventWaiter = new EventWaiter(Executors.newSingleThreadScheduledExecutor(), false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            eventWaiter.shutdown();
            jda.shutdown();
            managerBazyDanych.shutdown();
        }));
        eventBus.register(eventWaiter);
        eventBus.register(new Urlopy(managerBazyDanych, eventWaiter, jda));
        eventBus.register(new Weryfikacja(managerBazyDanych, jda));
    }

    public static void main(String[] args) {
        try {
            logger.info("Ładowanie...");
            if (args.length == 0) logger.error("Nie podano tokenu!");
            new Main(args[0]);
        } catch (Exception e) {
            logger.error("Wystąpił błąd!", e);
            System.exit(2);
        }
    }

}
