package io.github.flameyossnowy.universal.mongodb.parsers;

import java.util.List;

public record MongoConstraint<T>(String name, List<T> list) {
}
