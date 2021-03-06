package ru.goodsreview.core.db.entity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import ru.goodsreview.core.util.Batch;
import ru.goodsreview.core.util.Md5Helper;
import ru.goodsreview.core.util.Visitor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * User: daddy-bear
 * Date: 17.06.12
 * Time: 21:21
 */
public class EntityService {

    private final static String TYPE_ID_ATTR = "typeId";
    private final static String ID_ATTR = "id";

    private JdbcTemplate jdbcTemplate;


    @Required
    public void setJdbcTemplate(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void writeEntities(final Collection<JsonObject> entities) {

        final Batch<StorageEntity> batchForWrite = new Batch<StorageEntity>() {
            @Override
            public void handle(final List<StorageEntity> storageEntities) {
                jdbcTemplate.batchUpdate("INSERT INTO entity (entity_attrs, entity_hash, entity_type_id, entity_id) VALUES (?, ?, ?, ?)",
                        EntityBatchPreparedStatementSetter.of(storageEntities));
            }
        };

        final Batch<StorageEntity> batchForUpdate = new Batch<StorageEntity>() {
            @Override
            public void handle(final List<StorageEntity> storageEntities) {
                jdbcTemplate.batchUpdate("UPDATE entity SET entity_attrs = ?, entity_hash = ?, watch_date = ? WHERE entity_type_id = ? AND entity_id = ?",
                        EntityBatchPreparedStatementSetter.of(storageEntities));
            }
        };

        for (final JsonObject entity : entities) {
            final String hash = Md5Helper.hash(entity.toString());
            final long typeId = entity.get(TYPE_ID_ATTR).getAsLong();
            final long id = entity.get(ID_ATTR).getAsLong();

            String oldHash = jdbcTemplate.queryForObject("SELECT hash FROM entity WHERE entity_type_id = ? AND entity_id = ?", String.class);
            if (oldHash == null) {
                batchForWrite.submit(new StorageEntity(entity, hash, id, typeId));
            } else if (!oldHash.equals(hash)) {
                batchForUpdate.submit(new StorageEntity(entity, hash, id, typeId));
            }
        }

        batchForUpdate.flush();
        batchForWrite.flush();
    }

    public void visitEntities(final long entityTypeId, final Visitor<JsonObject> visitor) {

        final Gson gson = new Gson();

        jdbcTemplate.query("SELECT entity_attrs FROM entity WHERE entity_type_id = ?", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                visitor.visit(gson.toJsonTree(rs.getString("entity_attrs")).getAsJsonObject());
            }
        }, entityTypeId);
    }

}
