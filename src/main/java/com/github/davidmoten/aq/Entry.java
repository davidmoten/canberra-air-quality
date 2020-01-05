package com.github.davidmoten.aq;

import java.util.Date;
import java.util.Optional;

public final class Entry {
    public final String name;
    public final long time;
    public final Optional<Double> value;

    public Entry(String name, long time, Optional<Double> value) {
        this.name = name;
        this.time = time;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Entry [name=" + name + ", time=" + new Date(time) + ", value=" + value + "]";
    }

}