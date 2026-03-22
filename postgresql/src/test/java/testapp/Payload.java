package testapp;

import io.github.flameyossnowy.uniform.core.annotations.SerializedObject;

@SerializedObject
public class Payload {
    private String name;
    private int age;

    public Payload() {
    }

    public Payload(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }
}
