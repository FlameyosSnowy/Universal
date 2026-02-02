# Universal Relational Distributed Object Runtime

Universal is a powerful and fully featured Relational Distributed Object Runtime designed to work seamlessly with both SQL and NoSQL databases, and even File/Network repository adapters.
It provides a unified API to handle database interactions efficiently, making it easier to manage data persistence across different database types.

## Features
- **Cross-Database Compatibility**: Supports both relational (SQL) and document-based (NoSQL) databases.
- **Cross-Platform Repository Linking**: Link entities across different database adapters (e.g., MySQL ↔ MongoDB ↔ File ↔ Network).
- **Type Resolution System**: Handles type conversions seamlessly between Java objects and database representations.
- **Caching and Lazy loading**: Allows for automatic lazy loading and automatic caching.
- **Annotation-Based Configuration**: Define repositories, constraints, and conditions using a SQL-like syntax for both MongoDB and SQL.
- **Efficient Query Handling**: Uses built-in MongoDB methods and SQL functions without unnecessary query parsing.
- **Fully thread-safe**: Use Universal however you want, whenever you want, from any thread, and Universal will ensure it is fully safe.
- **High performance**: We take high performance measures to ensure:
    - **N + 1 Queries**: aggressive relationship cache to attempt solving N+1 query problem for relationships and reduce it to the minimum
    - **Query parsing**: We cache query building to ensure you never build something more than one time
    - **Connection pooling support**: Allowing connection pools is a primary goal for Universal to thrive on high concurrency.
    - **Reflection caching**: Despite Universal being heavily reflection-based, it takes extreme measures to ensure that reflection is not a big overhead by caching metadata and being [ASM](https://github.com/daniellansun/fast-reflection)-based.
    - **Batched relationships**: Universal tries to batch all relationship data, to use as little queries and achieve the highest throughput.
    - **Caching support**: Lots of caching, from Session caches, to Result caches, to Global caches, all being evicted and controlled.

## Supported Types
```java
import java.math.*;
import java.net.*;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository(name = "example")
public class Example {
    @Id
    private int id;

    private String text;
    private Integer integerObject;
    private int integerPrimitive;
    private Long longObject;
    private long longPrimitive;
    private Double doubleObject;
    private double doublePrimitive;
    private Float floatObject;
    private float floatPrimitive;
    private Boolean booleanObject;
    private boolean booleanPrimitive;
    private Short shortObject;
    private short shortPrimitive;
    private Byte byteObject;
    private byte bytePrimitive;
    private BigDecimal bigDecimal;
    private BigInteger bigInteger;
    private byte[] binary;
    private Timestamp timestamp;
    private Date date;
    private Time time;
    private LocalDate localDate;
    private LocalTime localTime;
    private LocalDateTime localDateTime;
    private OffsetDateTime offsetDateTime;
    private ZonedDateTime zonedDateTime;
    private Duration duration;
    private Period period;
    private URI uri;
    private URL url;
    private InetAddress inetAddress;
    private NetworkInterface networkInterface;
    private Class<?> type;
    private Locale locale;
    private Currency currency;
    private UUID uuid;
    
    private EnumType enumValue;

    @EnumAsOrdinal // Store the enum's ordinal in the database instead of the name
    private EnumType enumValueAsOrdinal;

    private List<String> list;
    private Set<Integer> set;
    private Map<String, String> map;
    private Map<String, List<String>> mapList;
    private Map<String, Map<String, String>> mapMap;
    private List<Map<String, String>> listMap;
    private Set<List<String>> setList;
    private List<List<String>> listList;
    private Set<Set<Integer>> setSet;
    private Map<String, Set<Integer>> mapSet;

    public enum EnumType { A, B, C }
}
```


## Installation

To include Universal in your project, add it as a dependency in your `pom.xml` (Maven) or `build.gradle`(`.kts`) (Gradle):

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>

<dependency>
  <groupId>com.github.FlameyosSnowy.Universal</groupId>
  <artifact>core</artifactId>
  <version>6.1.6</version>
</dependency>

<dependency>
  <groupId>com.github.FlameyosSnowy.Universal</groupId>
  <artifactId>PLATFORM</artifactId>
  <version>6.1.6</version>
</dependency>
```

```gradle
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.FlameyosSnowy.Universal:core:6.1.6")
    implementation("com.github.FlameyosSnowy.Universal:PLATFORM:6.1.6")
}
```

## Usage

### Defining Entities

Use annotations to define database entities:

```java
@Repository(name = "users_repo")
public class User {
    @Id
    private UUID id;
    private String username;
    private int age;
    
    // Getters and Setters
}
```

### Querying Data

Using the repository pattern:

```java
SQLiteRepositoryAdapter<User> adapter = SQLiteRepositoryAdapter.builder(User.class)
                .withCredentials(SQLiteCredentials.builder().directory("/home/flameyosflow/test.db").build())
                .build();
adapter.createRepository();
adapter.createRepository(); // if it doesn't exist.

List<User> minors = adapter.find(Query.select()
                .where("age", "<", 18)
                .orderBy("age", SortOrder.ASCENDING)
                .build());

for (User user : minors) {
    System.out.println(user);
}
```

For MongoDB:

```java
MongoRepositoryAdapter<User> adapter = MongoRepositoryAdapter.builder(User.class)
        .withCredentials(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("..."))
                .readConcern(ReadConcern.MAJORITY)
                .writeConcern(WriteConcern.W1)
                .build())
        .setDatabase("users")
        .build();
adapter.createRepository(); // if it doesn't exist.

List<User> minors = adapter.find(Query.select()
                .where("age", "<", 18)
                .orderBy("age", SortOrder.ASCENDING)
                .build());

for (User user : minors) {
    System.out.println(user);
}
```

## Cross-Platform Repository Linking

Universal now supports **cross-platform repository linking**, allowing entities backed by different storage systems to reference each other seamlessly.

### Example: User with External Cache

```java
// User entity in MySQL
@Repository(name = "users")
public class User {
    @Id
    private UUID id;
    
    private String username;
    
    // Reference to PathEntry stored in MongoDB
    @ExternalRepository(adapter = "cache-adapter")
    @OneToOne
    private PathEntry cachePath;
}

// PathEntry entity in MongoDB
@Repository(name = "path_entries")
public record PathEntry(
    @Id
    long id,
    Path entry,
    @OneToMany(mappedBy = Path.class) List<Path> directories,
    FileAttributes attributes
) {}

// Register adapters
MySQLRepositoryAdapter<User, UUID> userAdapter = MySQLRepositoryAdapter
    .builder(User.class, UUID.class)
    .withCredentials(mySQLCredentials)
    .build();

MongoRepositoryAdapter<PathEntry, Path> cacheAdapter = MongoRepositoryAdapter
    .builder(PathEntry.class, Path.class)
    .withCredentials(mongoCredentials)
    .build();

// Use cross-platform relationships
User user = userAdapter.findById(userId);
PathEntry cache = user.getCachePath(); // Automatically fetched from MongoDB!
```

## Supported Databases
- **SQL Databases**: MySQL, PostgreSQL, SQLite
- **NoSQL Databases**: MongoDB

## Contributing

1. Fork the repository.
2. Create a new branch.
3. Make your changes and commit them.
4. Push your branch and create a pull request.

## License

Universal is licensed under the MIT License. See `LICENSE` for details.
