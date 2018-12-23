package pl.fratik.FratikDev.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gg.amy.pgorm.annotations.PrimaryKey;
import gg.amy.pgorm.annotations.Table;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@Table("urlopy")
@Getter
public class Urlop implements DatabaseEntity {

    @PrimaryKey
    private String id;
    private Date dataOd;
    private Date dataDo;
    private String messageId;
    @Setter private boolean zatwierdzone = false;
    @Setter @Nullable private Date cooldownTo;
    @Setter private boolean valid = false;

    public Urlop() {

    }

    public Urlop(@NotNull String id, @NotNull Date dataOd, @NotNull Date dataDo, @NotNull String messageId) {
        this.id = id;
        this.dataOd = dataOd;
        this.dataDo = dataDo;
        this.messageId = messageId;
    }

    @Override
    @JsonIgnore
    public String getTableName() {
        return "urlopy";
    }
}
