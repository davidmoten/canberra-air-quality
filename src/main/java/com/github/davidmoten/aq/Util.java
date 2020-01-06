package com.github.davidmoten.aq;

import java.util.List;
import java.util.Optional;

final class Util {

    static void patchHoles(List<Entry> v) {
        // fix missing using average where possible or repeating previous value
        for (int i = 1; i < v.size() - 1; i++) {
            Entry entry = v.get(i);
            if (!entry.value.isPresent()) {
                if (v.get(i + 1).value.isPresent()) {
                    Entry entry2 = new Entry(entry.name, entry.time,
                            Optional.of((v.get(i - 1).value.get() + v.get(i + 1).value.get()) / 2));
                    v.set(i, entry2);
                } else {
                    Entry entry2 = new Entry(entry.name, entry.time, Optional.of(v.get(i - 1).value.get()));
                    v.set(i, entry2);
                }
            }
        }
    }

}
