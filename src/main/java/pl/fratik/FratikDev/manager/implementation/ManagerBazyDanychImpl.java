package pl.fratik.FratikDev.manager.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.amy.pgorm.PgStore;
import lombok.Getter;
import net.dv8tion.jda.core.entities.User;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.entity.DatabaseEntity;
import pl.fratik.FratikDev.entity.Urlop;
import pl.fratik.FratikDev.entity.WeryfikacjaInfo;
import pl.fratik.FratikDev.manager.ManagerBazyDanych;

import java.util.List;
import java.util.Optional;

public class ManagerBazyDanychImpl implements ManagerBazyDanych {
    private final Logger logger;
    @Getter
    private PgStore pgStore;

    public ManagerBazyDanychImpl() {
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void shutdown() {
        if (pgStore != null) pgStore.disconnect();
    }

    @Override
    public void load() {
        Config ustawienia = Config.instance;
        pgStore = new PgStore(ustawienia.database.jdbcUrl, ustawienia.database.user, ustawienia.database.password);
        pgStore.connect();
    }

    @Override
    public Urlop getUrlop(User user) {
        Optional<Urlop> data = pgStore.mapSync(Urlop.class).load(user.getId());
        return data.orElse(null);
    }

    @Override
    public List<Urlop> getAllUrlopy() {
        return pgStore.mapSync(Urlop.class).loadAll();
    }

    @Override
    public WeryfikacjaInfo getWeryfikacja(User user) {
        Optional<WeryfikacjaInfo> data = pgStore.mapSync(WeryfikacjaInfo.class).load(user.getId());
        return data.orElse(null);
    }

//    @Override
//    public List<WeryfikacjaInfo> getAllWeryfikacje() {
//        return pgStore.mapSync(WeryfikacjaInfo.class).loadAll();
//    }

    @Override
    public void usunUrlop(User user) {
        pgStore.mapSync(Urlop.class).delete(user.getId());
    }

    @Override
    public void usunWeryfikacje(User user) {
        pgStore.mapSync(WeryfikacjaInfo.class).delete(user.getId());
    }

    @Override
    public void save(@NotNull DatabaseEntity entity) {
        ObjectMapper mapper = new ObjectMapper();
        String JSONed;
        try { JSONed = mapper.writeValueAsString(entity); } catch (Exception ignored) { JSONed = entity.toString(); }
        logger.debug("Zmiana danych w DB: {} -> {} -> {}", entity.getTableName(), entity.getClass().getName(), JSONed);
        if (entity instanceof Urlop) save((Urlop) entity);

    }

    private void save(@NotNull Urlop config) {
        pgStore.mapSync(Urlop.class).save(config);
    }

}
