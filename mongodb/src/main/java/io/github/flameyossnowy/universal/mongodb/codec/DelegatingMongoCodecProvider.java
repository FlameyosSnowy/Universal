package io.github.flameyossnowy.universal.mongodb.codec;

import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

public final class DelegatingMongoCodecProvider implements CodecProvider {

    private volatile MongoTypeCodecProvider context;

    public void bind(MongoTypeCodecProvider context) {
        this.context = context;
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        // Two reasons for putting this in a local variable:
        // - Avoids multiple volatile reads (faster especially in tight loops)
        // - Prevents race conditions mid-method
        MongoTypeCodecProvider ctx = context;
        if (ctx == null) return null;

        return ctx.get(clazz, registry);
    }
}
