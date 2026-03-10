import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.mongodb.result.MongoDatabaseResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MongoDatabaseResultTest {

    @Test
    void getWrapsPrimitiveTypes() {
        Document doc = new Document();
        doc.put("i", 123);
        doc.put("b", true);

        CollectionHandler handler = mock(CollectionHandler.class);
        RepositoryModel<?, ?> model = mock(RepositoryModel.class);

        MongoDatabaseResult result = new MongoDatabaseResult(doc, handler, model);

        assertEquals(123, result.get("i", int.class));
        assertEquals(true, result.get("b", boolean.class));
    }

    @Test
    void hasColumnColumnCountAndColumnNameBehaveForNullDocument() {
        CollectionHandler handler = mock(CollectionHandler.class);
        RepositoryModel<?, ?> model = mock(RepositoryModel.class);

        MongoDatabaseResult result = new MongoDatabaseResult(null, handler, model);

        assertFalse(result.hasColumn("x"));
        assertEquals(0, result.getColumnCount());
        assertNull(result.getColumnName(0));
        assertNull(result.get("x", String.class));
    }

    @Test
    void columnNamesAreLazilyComputedAndReflectInitialDocument() {
        Document doc = new Document();
        doc.put("a", 1);
        doc.put("b", 2);

        CollectionHandler handler = mock(CollectionHandler.class);
        RepositoryModel<?, ?> model = mock(RepositoryModel.class);

        MongoDatabaseResult result = new MongoDatabaseResult(doc, handler, model);

        assertEquals(2, result.getColumnCount());
        String name0 = result.getColumnName(0);
        String name1 = result.getColumnName(1);
        assertNotNull(name0);
        assertNotNull(name1);
        assertNotEquals(name0, name1);

        assertTrue(result.hasColumn("a"));
        assertTrue(result.hasColumn("b"));
    }

    @Test
    void setDocumentChangesBackingDocument() {
        CollectionHandler handler = mock(CollectionHandler.class);
        RepositoryModel<?, ?> model = mock(RepositoryModel.class);

        MongoDatabaseResult result = new MongoDatabaseResult(new Document("x", 1), handler, model);
        assertTrue(result.hasColumn("x"));

        result.setDocument(new Document("y", 2));
        assertFalse(result.hasColumn("x"));
        assertTrue(result.hasColumn("y"));
        assertEquals(1, result.getColumnCount());
    }
}
