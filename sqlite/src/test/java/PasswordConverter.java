import io.github.flameyossnowy.universal.sqlite.resolvers.SQLiteValueTypeResolver;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PasswordConverter implements SQLiteValueTypeResolver<Password> {
    @Override
    public Password resolve(final ResultSet resultSet, final String columnLabel) throws Exception {
        return new Password(resultSet.getString(columnLabel));
    }

    @Override
    public void insert(final PreparedStatement statement, final int parameterIndex, final Password value) throws Exception {
        statement.setString(parameterIndex, value.password());
    }

    @Override
    public Class<?> encodedType() {
        return String.class;
    }
}
