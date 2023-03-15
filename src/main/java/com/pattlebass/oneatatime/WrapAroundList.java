package com.pattlebass.oneatatime;

import java.util.ArrayList;

public class WrapAroundList<E> extends ArrayList<E> {
    public E getNext(E uid) {
        int idx = this.indexOf(uid);
        if (idx + 1 == this.size()) return this.get(0);
        return this.get(idx + 1);
    }

    public E getPrevious(E uid) {
        int idx = this.indexOf(uid);
        if (idx == 0) return this.get(this.size() - 1);
        return this.get(idx - 1);
    }
}
