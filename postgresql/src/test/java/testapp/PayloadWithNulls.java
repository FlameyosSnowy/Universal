package testapp;

import io.github.flameyossnowy.uniform.core.annotations.SerializedObject;

@SerializedObject
public class PayloadWithNulls {
    private String a;
    private String b;

    public PayloadWithNulls() {
    }

    public PayloadWithNulls(String a, String b) {
        this.a = a;
        this.b = b;
    }

    public String getA() {
        return a;
    }

    public String getB() {
        return b;
    }

    public void setA(String a) {
        this.a = a;
    }

    public void setB(String b) {
        this.b = b;
    }
}
