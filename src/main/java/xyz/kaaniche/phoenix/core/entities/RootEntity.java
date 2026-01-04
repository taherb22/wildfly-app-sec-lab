package xyz.kaaniche.phoenix.core.entities;

import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;

@MappedSuperclass
public abstract class RootEntity<ID> implements Serializable {
    public abstract ID getId();
    public abstract void setId(ID id);
}
