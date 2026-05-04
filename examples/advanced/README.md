# Advanced Warps & Factions Example

This example demonstrates the full capabilities of the Universal ORM framework with complex entity relationships, JSON fields, caching, and transactions.

## Entities Overview

### Warp
Teleport location with:
- **OneToOne**: Home warp relationship with Faction
- **ManyToOne**: Regular faction ownership
- **JSON field**: `WarpMetadata` with tags, permissions, particle settings
- **Indexes**: On `world`, `isPublic` + `world`
- **Caching**: LRU cache with 512 entries

### Faction
Player organization with:
- **OneToOne**: Home warp (bidirectional)
- **OneToMany**: List of members (FactionMember)
- **OneToMany**: List of warps
- **ManyToOne**: Alliance with another faction
- **JSON field**: `FactionSettings` with permissions, MOTD, limits
- **Constraints**: Power validation
- **Indexes**: Unique on name, power, leader

### FactionMember
Player-faction relationship with:
- **ManyToOne**: Faction ownership
- **Indexes**: Unique on playerId, normal on faction

## Key Features Demonstrated

### 1. Relationship Types
```java
// OneToOne bidirectional
@OneToOne(mappedBy = "homeWarp", lazy = true)
private Warp homeWarp;

// OneToMany with lazy loading
@OneToMany(mappedBy = FactionMember.class, lazy = true)
private List<FactionMember> members;

// ManyToOne
@ManyToOne(join = "factions")
private Faction faction;
```

### 2. JSON Fields
```java
@JsonField(storage = JsonField.Storage.COLUMN, queryable = true)
private WarpMetadata metadata;
```

### 3. Indexes & Constraints
```java
@Index(name = "idx_faction_name", fields = {"name"}, type = IndexType.UNIQUE)
@Constraint(name = "chk_power", fields = {"power", "maxPower"})
```

### 4. Repository Initialization
```java
MySQLRepositoryAdapter<Faction, UUID> adapter = MySQLRepositoryAdapter
    .<Faction, UUID>builder(Faction.class, UUID.class)
    .withCredentials(credentials)
    .withConnectionProvider(MySQLHikariConnectionProvider::new)
    .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
    .build();
```

## Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Universal ORM
    implementation("io.github.flameyossnowy:universal-core:VERSION")
    implementation("io.github.flameyossnowy:universal-mysql:VERSION")
    
    // HikariCP for connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")
}
```

## Running the Example

1. Start MySQL database
2. Create database: `CREATE DATABASE faction_db;`
3. Update credentials in `FactionSystemExample.java`
4. Run: `java examples.advanced.FactionSystemExample`

## Generated SQL Schema

The compile-time annotation processor generates:
- Tables with proper foreign keys
- Indexes for query optimization
- JSON columns for metadata fields
- Constraint checks for business rules
