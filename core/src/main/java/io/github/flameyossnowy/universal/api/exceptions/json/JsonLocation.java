package io.github.flameyossnowy.universal.api.exceptions.json;

public record JsonLocation(int lineNumber, int columnNumber, long charOffset, long byteOffset) {

}
