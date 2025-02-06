import io.github.flameyossnowy.universal.mongodb.resolvers.MongoValueTypeResolver;

public class PasswordConverter implements MongoValueTypeResolver<String, Password> {
    @Override
    public Password decode(final String encoded) {
        return new Password(encoded);
    }

    @Override
    public String encode(final Password value) {
        return value.password();
    }

    @Override
    public Class<String> encodedType() {
        return String.class;
    }
}
