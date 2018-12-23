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

    public WeryfikacjaInfo() {}

    public WeryfikacjaInfo(String id) {
        this.id = id;
    }

    @Override
    @JsonIgnore
    public String getTableName() {
        return "weryfikacja";
    }

}
