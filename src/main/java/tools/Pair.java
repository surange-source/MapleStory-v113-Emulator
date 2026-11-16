package tools;

import java.io.Serializable;

public record Pair<E, F>(E left, F right) implements Serializable {}
