# Universal ORM/ODM

Universal is a powerful and flexible Object-Relational Mapper (ORM) and Object-Document Mapper (ODM) designed to work seamlessly with both SQL and NoSQL databases. It provides a unified API to handle database interactions efficiently, making it easier to manage data persistence across different database types.

## Features

- **Cross-Database Compatibility**: Supports both relational (SQL) and document-based (NoSQL) databases.
- **Type Resolution System**: Handles type conversions seamlessly between Java objects and database representations.
- **Annotation-Based Configuration**: Define repositories, constraints, and conditions using a SQL-like syntax for both MongoDB and SQL.
- **MongoDB & SQL Support**: Provides adapters for both MongoDB and SQL databases.
- **Efficient Query Handling**: Uses built-in MongoDB methods and SQL functions without unnecessary query parsing.
- **Lightweight**: Only ~250kb for one Platform + Core.

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
  <version>2.0.0</version>
</dependency>

<dependency>
  <groupId>com.github.FlameyosSnowy.Universal</groupId>
  <artifactId>PLATFORM</artifactId>
  <version>2.0.0</version>
</dependency>
```

```gradle
repositories {
    maven("https://repo.foxikle.dev/flameyos")
}

dependencies {
    implementation("com.github.FlameyosSnowy.Universal:core:2.0.0")
    implementation("com.github.FlameyosSnowy.Universal:PLATFORM:2.0.0")
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

## Supported Databases
- **SQL Databases**: MySQL and, SQLite.
- **NoSQL Databases**: MongoDB.

## Contributing

1. Fork the repository.
2. Create a new branch.
3. Make your changes and commit them.
4. Push your branch and create a pull request.

## License

Universal is licensed under the MIT License. See `LICENSE` for details.
