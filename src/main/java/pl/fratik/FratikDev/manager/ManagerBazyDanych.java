package pl.fratik.FratikDev.manager;

import net.dv8tion.jda.core.entities.User;
import org.jetbrains.annotations.NotNull;
import pl.fratik.FratikDev.entity.DatabaseEntity;
import pl.fratik.FratikDev.entity.Urlop;
import pl.fratik.FratikDev.entity.WeryfikacjaInfo;

import java.util.List;

public interface ManagerBazyDanych {
    void load();

    void shutdown();

    Urlop getUrlop(User user);

    List<Urlop> getAllUrlopy();

    WeryfikacjaInfo getWeryfikacja(User user);

//    List<WeryfikacjaInfo> getAllWeryfikacje();

    void usunUrlop(User user);

    void usunWeryfikacje(User user);

    void save(@NotNull DatabaseEntity entity);
}
