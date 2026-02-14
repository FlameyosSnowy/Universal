import io.github.flameyossnowy.universal.mongodb.codec.MongoJsonCodecBridge;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MongoJsonCodecBridgeTest {

    @Test
    void jsonObject_to_document() {
        Object value = MongoJsonCodecBridge.jsonToBsonFriendly("{\"a\":1,\"b\":{\"c\":\"x\"}}");
        assertInstanceOf(Document.class, value);
        Document doc = (Document) value;
        assertEquals(1, doc.get("a"));
        assertInstanceOf(Document.class, doc.get("b"));
        assertEquals("x", ((Document) doc.get("b")).get("c"));
    }

    @Test
    void jsonArray_to_list() {
        Object value = MongoJsonCodecBridge.jsonToBsonFriendly("[1,2,3]");
        assertInstanceOf(List.class, value);
        assertEquals(List.of(1, 2, 3), value);
    }
}
