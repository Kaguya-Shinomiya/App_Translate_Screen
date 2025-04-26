package com.example.screentranslator;

import java.util.Objects;

public class TextBlockInfo {
    public final String text;
    public final int x;
    public final int y;

    public TextBlockInfo(String text, int x, int y) {
        this.text = text;
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextBlockInfo)) return false;
        TextBlockInfo that = (TextBlockInfo) o;
        return x == that.x && y == that.y && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, x, y);
    }
}
