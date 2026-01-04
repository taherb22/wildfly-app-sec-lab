package xyz.kaaniche.phoenix.core.entities;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class CompoundPKEntity<ID extends CompoundPK> extends RootEntity<ID> {
    @EmbeddedId
    private ID id;

    @Override
    public ID getId() {
        return id;
    }

    @Override
    public void setId(ID id) {
        this.id = id;
    }
}
