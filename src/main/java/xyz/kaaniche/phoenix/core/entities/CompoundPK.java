package xyz.kaaniche.phoenix.core.entities;

import java.io.Serializable;
import java.util.Objects;

public abstract class CompoundPK implements Serializable {
    private final Class<?> type;

    protected CompoundPK(Class<?> type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(type, ((CompoundPK) o).type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }
}
