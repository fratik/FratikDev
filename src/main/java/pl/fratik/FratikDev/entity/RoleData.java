package pl.fratik.FratikDev.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gg.amy.pgorm.annotations.PrimaryKey;
import gg.amy.pgorm.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("role")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleData implements DatabaseEntity {

    @PrimaryKey
    private String userId;
    private String roleId;
    private boolean blacklist = false;
    private Type type;

    @Override
    @JsonIgnore
    public String getTableName() {
        return "role";
    }

    public enum Type {
        BOOSTER,
        REWARD,
        UNKNOWN
    }
}
