package com.encdata.astronautics.space;

import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

public final class SpaceGridEntityGetter implements LevelEntityGetter<Entity> {
    private final LevelEntityGetter<Entity> delegate;

    public SpaceGridEntityGetter(LevelEntityGetter<Entity> delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable Entity get(int id) {
        Entity entity = delegate.get(id);
        return entity != null && !SpaceGrids.seesContext(entity) ? null : entity;
    }

    @Override
    public @Nullable Entity get(UUID uuid) {
        Entity entity = delegate.get(uuid);
        return entity != null && !SpaceGrids.seesContext(entity) ? null : entity;
    }

    @Override
    public Iterable<Entity> getAll() {
        return Iterables.filter(delegate.getAll(), SpaceGrids::seesContext);
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
        delegate.get(test, entity -> SpaceGrids.seesContext(entity)
                ? consumer.accept(entity)
                : AbortableIterationConsumer.Continuation.CONTINUE);
    }

    @Override
    public void get(AABB area, Consumer<Entity> consumer) {
        SpaceGridAddress viewer = SpaceGrids.context();
        if (viewer == null) {
            delegate.get(area, consumer);
            return;
        }
        delegate.get(area, entity -> {
            if (SpaceGrids.sees(viewer, entity)) consumer.accept(entity);
        });
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB area,
                                       AbortableIterationConsumer<U> consumer) {
        SpaceGridAddress viewer = SpaceGrids.context();
        if (viewer == null) {
            delegate.get(test, area, consumer);
            return;
        }
        delegate.get(test, area, entity -> SpaceGrids.sees(viewer, entity)
                ? consumer.accept(entity)
                : AbortableIterationConsumer.Continuation.CONTINUE);
    }
}
