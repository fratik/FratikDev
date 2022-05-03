package pl.fratik.FratikDev.manager.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.amy.pgorm.PgStore;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.fratik.FratikDev.Config;
import pl.fratik.FratikDev.entity.*;
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
    public Urlop getUrlopByMessageId(String id) {
        List<Urlop> data = pgStore.mapSync(Urlop.class).loadManyBySubkey("data->>'messageId'", id);
        return data.isEmpty() ? null : data.get(0);
    }

    @Override
    public SuffixData getSuffix(Guild guild) {
        return pgStore.mapSync(SuffixData.class).load(guild.getId()).orElse(null);
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

    @Override
    public RoleData getRoleData(User user) {
        return pgStore.mapSync(RoleData.class).load(user.getId()).orElse(null);
    }

    @Override
    public List<RoleData> getRoleDataByRole(Role role) {
        return pgStore.mapSync(RoleData.class).loadManyBySubkey("data->>'roleId'", role.getId());
    }

    @Override
    public List<RoleData> getAllRoleData() {
        return pgStore.mapSync(RoleData.class).loadAll();
    }

    @Override
    public void usunRole(User user) {
        usunRole(user.getId());
    }

    public void usunRole(String id) {
        pgStore.mapSync(RoleData.class).delete(id);
    }

    //    @Override
//    public List<WeryfikacjaInfo> getAllWeryfikacje() {
//        return pgStore.mapSync(WeryfikacjaInfo.class).loadAll();
//    }

    @Override
    public void usunUrlop(User user) {
        usunUrlop(user.getId());
    }

    public void usunUrlop(String id) {
        pgStore.mapSync(Urlop.class).delete(id);
    }

    @Override
    public void usunWeryfikacje(User user) {
        pgStore.mapSync(WeryfikacjaInfo.class).delete(user.getId());
    }

    @Override
    public void usunSuffix(Guild guild) {
        pgStore.mapSync(SuffixData.class).delete(guild.getId());
    }

    @Override
    public void save(@NotNull DatabaseEntity entity) {
        ObjectMapper mapper = new ObjectMapper();
        String JSONed;
        try { JSONed = mapper.writeValueAsString(entity); } catch (Exception ignored) { JSONed = entity.toString(); }
        logger.debug("Zmiana danych w DB: {} -> {} -> {}", entity.getTableName(), entity.getClass().getName(), JSONed);
        if (entity instanceof Urlop) save((Urlop) entity);
        if (entity instanceof WeryfikacjaInfo) save((WeryfikacjaInfo) entity);
        if (entity instanceof SuffixData) save((SuffixData) entity);
        if (entity instanceof RoleData) save((RoleData) entity);
    }

    private void save(@NotNull Urlop config) {
        pgStore.mapSync(Urlop.class).save(config);
    }

    private void save(@NotNull WeryfikacjaInfo config) {
        pgStore.mapSync(WeryfikacjaInfo.class).save(config);
    }

    private void save(@NotNull SuffixData config) {
        pgStore.mapSync(SuffixData.class).save(config);
    }

    private void save(@NotNull RoleData config) {
        pgStore.mapSync(RoleData.class).save(config);
    }

}
