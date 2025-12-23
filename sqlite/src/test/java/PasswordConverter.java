import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

public class PasswordConverter implements TypeResolver<Password> {
    @Override
    public Class<Password> getType() {
        return Password.class;
    }

    @Override
    public Class<?> getDatabaseType() {
        return String.class;
    }

    @Override
    public Password resolve(DatabaseResult result, String columnName) {
        return new Password(result.get(columnName, String.class));
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Password value) {
        parameters.set(index, value.password(), String.class);
    }
}
