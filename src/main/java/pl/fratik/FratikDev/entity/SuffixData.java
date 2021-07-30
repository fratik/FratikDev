package pl.fratik.FratikDev.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gg.amy.pgorm.annotations.PrimaryKey;
import gg.amy.pgorm.annotations.Table;
import lombok.*;

import java.util.Date;

@Table("suffix")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SuffixData implements DatabaseEntity {

    @PrimaryKey
    private String guildId;
    private String suffix;

    @Override
    @JsonIgnore
    public String getTableName() {
        return "suffix";
    }

}
