package pl.fratik.FratikDev.manager;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import pl.fratik.FratikDev.entity.*;

import java.util.List;

public interface ManagerBazyDanych {
    void load();

    void shutdown();

    Urlop getUrlop(User user);

    Urlop getUrlopByMessageId(String id);

    SuffixData getSuffix(Guild guild);

    List<Urlop> getAllUrlopy();

    WeryfikacjaInfo getWeryfikacja(User user);

    RoleData getRoleData(User user);

    List<RoleData> getRoleDataByRole(Role role);

//    List<WeryfikacjaInfo> getAllWeryfikacje();

    void usunUrlop(User user);

    void usunUrlop(String id);

    void usunWeryfikacje(User user);

    void usunSuffix(Guild guild);

    List<RoleData> getAllRoleData();

    void usunRole(User user);

    void save(@NotNull DatabaseEntity entity);
}
