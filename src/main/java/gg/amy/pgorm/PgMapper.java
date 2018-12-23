package gg.amy.pgorm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.amy.pgorm.annotations.BtreeIndex;
import gg.amy.pgorm.annotations.GIndex;
import gg.amy.pgorm.annotations.PrimaryKey;
import gg.amy.pgorm.annotations.Table;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NOTE: The JSONB data column is always named <code>data</code>.
 *
 * @author amy
 * @since 4/10/18.
 */
@SuppressWarnings({"WeakerAccess", "unused", "SpellCheckingInspection"})
public class PgMapper<T> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Getter
    private final Class<T> type;
    @Getter
    private final PgStore store;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Field pkField;
    private Table table;
    private PrimaryKey primaryKey;

    public PgMapper(final PgStore store, final Class<T> type) {
        this.store = store;
        this.type = type;
        init();
    }

    private void init() {
        // Scan to ensure required annotations
        if(!type.isAnnotationPresent(Table.class)) {
            throw new IllegalStateException("Got class " + type.getName() + " to map, but it has no @Table!?");
        }
        table = type.getDeclaredAnnotation(Table.class);
        // Scan the class for a primary key
        boolean havePk = false;
        pkField = null;
        for(final Field field : type.getDeclaredFields()) {
            field.setAccessible(true);
            if(field.isAnnotationPresent(PrimaryKey.class)) {
                havePk = true;
                pkField = field;
            }
        }
        if(!havePk) {
            throw new IllegalStateException("Class " + type.getName() + " has no @PrimaryKey!?");
        }
        // Ensure that it's a valid type
        final String sqlType = typeToSqlType(pkField.getType());
        primaryKey = pkField.getDeclaredAnnotation(PrimaryKey.class);
        // Create the table
        store.sql("CREATE TABLE IF NOT EXISTS " + table.value() + " (" +
                primaryKey.value() + ' ' + sqlType + " PRIMARY KEY NOT NULL UNIQUE," +
                "data JSONB" +
                ");");
        logger.info("Created table {} for entity class {}.", table.value(), type.getName());
        // Create the indexes
        if(type.isAnnotationPresent(BtreeIndex.class)) {
            final BtreeIndex btreeIndex = type.getDeclaredAnnotation(BtreeIndex.class);
            for(final String s : btreeIndex.value()) {
                final String idx = "idx_btree_" + table.value() + '_' + s;
                store.sql("CREATE INDEX IF NOT EXISTS " + idx + " ON " + table.value() + " USING BTREE ((data->'" + s + "'));");
                logger.info("Created index {} on {} for entity class {}.", idx, table.value(), type.getName());
            }
        }
        // Make base GIN index
        final String dataGinIdx = "idx_gin_" + table.value() + "_data";
        store.sql("CREATE INDEX IF NOT EXISTS " + dataGinIdx + " ON " + table.value() + " USING GIN (data);");
        logger.info("Created index idx_gin_data on {} for entity class {}.", table.value(), type.getName());
        if(type.isAnnotationPresent(GIndex.class)) {
            final GIndex gin = type.getDeclaredAnnotation(GIndex.class);
            for(final String s : gin.value()) {
                final String idx = "idx_gin_" + table.value() + '_' + s;
                store.sql("CREATE INDEX IF NOT EXISTS " + idx + " ON " + table.value() + " USING GIN ((data->'" + s + "'));");
                logger.info("Created index {} on {} for entity class {}.", idx, table.value(), type.getName());
            }
        }
    }

    public void save(final T entity) {
        pkField.setAccessible(true);
        try {
            final Object pk = pkField.get(entity);
            // Map the object to JSON
            final String json = MAPPER.writeValueAsString(entity);
            // Oh god this is so ugly
            store.sql("INSERT INTO " + table.value() + " (" + primaryKey.value() + ", data) values (?, to_jsonb(?::jsonb)) " +
                    "ON CONFLICT (" + primaryKey.value() + ") DO UPDATE SET " + primaryKey.value() + " = ?, data = to_jsonb(?::jsonb);", c -> {
                c.setObject(1, pk);
                c.setString(2, json);
                c.setObject(3, pk);
                c.setString(4, json);
                c.execute();
            });
        } catch(final IllegalAccessException e) {
            logger.error("Couldn't access primary key for entity {} (value: {}): {}", type.getName(), entity, e);
        } catch(final JsonProcessingException e) {
            logger.error("Couldn't map entity {} (value: {}) to JSON: {}", type.getName(), entity, e);
        }
    }

    public Optional<T> load(final Object pk) {
        final OptionalHolder result = new OptionalHolder();
        store.sql("SELECT * FROM " + table.value() + " WHERE " + primaryKey.value() + " = ?;", c -> {
            c.setObject(1, pk);
            final ResultSet resultSet = c.executeQuery();
            if(resultSet.isBeforeFirst()) {
                resultSet.next();
                try {
                    result.setValue(loadFromResultSet(resultSet));
                } catch(final IllegalStateException e) {
                    e.printStackTrace();
                    // Optional API says this will return Optional.empty()
                    result.setValue(null);
                }
            }
        });
        return result.value;
    }

    public void delete(final Object pk) {
        store.sql("DELETE FROM " + table.value() + " WHERE " + primaryKey.value() + " = ?;", c -> {
            c.setObject(1, pk);
            c.execute();
        });
    }

    /**
     * This is a slightly-weird thing, but it makes sense given the kind of
     * use-case I have. <p/>
     * Suppose you have many documents like this:
     * <pre>
     * {
     *     "id": 123456789,
     *     "data": "henlo world",
     *     "type": "type.whatever"
     * }
     * </pre>
     * and you want to select all documents with type {@code type.whatever}.
     * This can't be done nicely without writing SQL statements directly in
     * your application, which is ugly in my opinion. To solve this, there has
     * to be a method that allows loading many objects by a key's value in the
     * JSONB data that gets stored, hence why this method exists.
     *
     * @param subKey     The subkey to query on. Ex. {@code data->'type'}.
     * @param subKeyData The subkey data to search for. Ex.
     *                   {@code type.whatever}.
     *
     * @return A list of {@code <T>}s that has the given value for the given
     * subkey.
     */
    public List<T> loadManyBySubkey(final String subKey, final String subKeyData) {
        final List<T> data = new ArrayList<>();
        store.sql("SELECT * FROM " + table.value() + " WHERE " + subKey + " = ?;", c -> {
            c.setObject(1, subKeyData);
            final ResultSet resultSet = c.executeQuery();
            if(resultSet.isBeforeFirst()) {
                while(resultSet.next()) {
                    try {
                        data.add(loadFromResultSet(resultSet));
                    } catch(final IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return data;
    }

    public List<T> loadManyBySubkey(final String subKey, final String subKeyData, final int limit) {
        final List<T> data = new ArrayList<>();
        store.sql("SELECT * FROM " + table.value() + " WHERE " + subKey + " = ? LIMIT " + limit + ";", c -> {
            c.setObject(1, subKeyData);
            final ResultSet resultSet = c.executeQuery();
            if(resultSet.isBeforeFirst()) {
                while(resultSet.next()) {
                    try {
                        data.add(loadFromResultSet(resultSet));
                    } catch(final IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return data;
    }

    public List<T> loadAll() {
        final List<T> data = new ArrayList<>();
        store.sql("SELECT * FROM " + table.value() + ";", c -> {
            final ResultSet resultSet = c.executeQuery();
            if (resultSet.isBeforeFirst()) {
                while(resultSet.next()) {
                    try {
                        data.add(loadFromResultSet(resultSet));
                    } catch(final IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return data;
    }

    public List<T> sum(final String columnName, final String where) {
        final List<T> data = new ArrayList<>();
        store.sql("SELECT SUM(" + columnName + ") FROM " + table.value() + " WHERE " + where + " ;", c -> {
            final ResultSet resultSet = c.executeQuery();
            if (resultSet.isBeforeFirst()) {
                while(resultSet.next()) {
                    try {
                        data.add(loadFromResultSet(resultSet));
                    } catch(final IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return data;
    }

    public List<T> loadRaw(String sqlBase, String... arguments) {
        final List<T> data = new ArrayList<>();
        store.sql(String.format(sqlBase, table.value()), c -> {
            int I = 0;
            for (String arg : arguments) {
                I++;
                c.setObject(I, arg);
            }
            final ResultSet resultSet = c.executeQuery();
            if (resultSet.isBeforeFirst()) {
                while(resultSet.next()) {
                    try {
                        data.add(loadFromResultSet(resultSet));
                    } catch(final IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return data;
    }

    public T loadFromResultSet(final ResultSet resultSet) {
        try {
            final String json = resultSet.getString("data");
            try {
                return MAPPER.readValue(json, type);
            } catch(final IOException e) {
                logger.error("Couldn't load entity {} from JSON {}: {}", type.getName(), json, e);
                throw new IllegalStateException("Couldn't load entity " + type.getName() + " from JSON " + json, e);
            }
        } catch(final SQLException e) {
            logger.error("Couldn't load entity {} from JSON: {}", type.getName(), e);
            throw new IllegalStateException("Couldn't load entity " + type.getName(), e);
        }
    }

    private String typeToSqlType(final Class<?> type) {
        if(type.equals(String.class)) {
            return "TEXT";
        } else if(type.equals(Integer.class) || type.equals(int.class)) {
            return "INT";
        } else if(type.equals(Long.class) || type.equals(long.class)) {
            return "BIGINT";
        } else {
            throw new IllegalArgumentException("No SQL type mapping known for class of type: " + type.getName());
        }
    }

    public String getTableName() {
        return table.value();
    }

    public String getPrimaryKeyName() {
        return primaryKey.value();
    }

    // Ugly hack to allow bringing an optional out of a lambda
    private final class OptionalHolder {
        // This is intentionally done. . _.
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private Optional<T> value = Optional.empty();

        private void setValue(final T data) {
            value = Optional.ofNullable(data);
        }
    }
}