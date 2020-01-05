package com.github.davidmoten.aq;

import java.util.List;

import org.ejml.simple.SimpleMatrix;

public final class Result {
    private final List<Entry> list;
    private final SimpleMatrix z;

    public Result(List<Entry> list, SimpleMatrix z) {
        this.list = list;
        this.z = z;
    }

    public List<Entry> entries() {
        return list;
    }

    public SimpleMatrix z() {
        return z;
    }
}