package pl.fratik.FratikDev.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gg.amy.pgorm.annotations.PrimaryKey;
import gg.amy.pgorm.annotations.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Table("weryfikacja")
@Getter
@Setter
public class WeryfikacjaInfo implements DatabaseEntity {

    @PrimaryKey
    private String id;
    private Date weryfikacja;
    private Date ostatniaWiadomosc;
    private int ileRazy = 1;
    private String nickname;
    private Boolean nicknameBlacklist;

    public WeryfikacjaInfo() {}

    public WeryfikacjaInfo(String id) {
        this.id = id;
    }

    public boolean isNicknameBlacklist() {
        return nicknameBlacklist != null && nicknameBlacklist;
    }

    @Override
    @JsonIgnore
    public String getTableName() {
        return "weryfikacja";
    }

}
