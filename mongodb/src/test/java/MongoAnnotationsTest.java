import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MongoAnnotationsTest {

    @Test
    void generated_metadata_exposes_flags_for_common_annotations() {
        ModelsBootstrap.init();

        RepositoryModel<MongoAnnotatedEntity, Long> model = io.github.flameyossnowy.universal.api.meta.GeneratedMetadata.getByEntityClass(MongoAnnotatedEntity.class);
        assertNotNull(model);

        FieldModel<MongoAnnotatedEntity> updatedAt = model.fieldByName("updatedAt");
        assertNotNull(updatedAt);
        assertTrue(updatedAt.hasNowAnnotation());

        FieldModel<MongoAnnotatedEntity> greeting = model.fieldByName("greeting");
        assertNotNull(greeting);
        assertEquals("hello", greeting.defaultValue());

        FieldModel<MongoAnnotatedEntity> status = model.fieldByName("status");
        assertNotNull(status);
        assertTrue(status.enumAsOrdinal());

        FieldModel<MongoAnnotatedEntity> parent = model.fieldByName("parent");
        assertNotNull(parent);
        assertNotNull(parent.onDelete());
        assertNotNull(parent.onUpdate());
        assertEquals(io.github.flameyossnowy.universal.api.annotations.enums.OnModify.CASCADE, parent.onDelete().value());
        assertEquals(io.github.flameyossnowy.universal.api.annotations.enums.OnModify.RESTRICT, parent.onUpdate().value());
    }
}
