package xyz.kaaniche.phoenix.core.controllers;

import xyz.kaaniche.phoenix.core.entities.RootEntity;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface GenericDAO<E extends RootEntity<ID>, ID extends Serializable> {
    <S extends E> S save(S entity);
    E edit(ID id, Consumer<E> updateFewAttributes);
    void delete(E entity);
    Optional<E> findById(ID id);
    List<E> findAll();
    Class<E> getEntityClass();
}
