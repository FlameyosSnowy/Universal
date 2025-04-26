import io.github.flameyossnowy.universal.sql.resolvers.SQLValueTypeResolver;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;

public class CustomType implements SQLValueTypeResolver<Level> {
    @Override
    public Level resolve(ResultSet resultSet, String columnLabel) throws Exception {
        return Level.parse(resultSet.getString(columnLabel));
    }

    @Override
    public void insert(PreparedStatement statement, int parameterIndex, Level value) throws Exception {
        statement.setString(parameterIndex, value.toString());
    }

    @Override
    public Class<?> encodedType() {
        return String.class;
    }
}
