package org.zalando.nakadi.repository;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.zalando.nakadi.domain.ConsumedEvent;
import org.zalando.nakadi.domain.EventTypePartition;
import org.zalando.nakadi.domain.NakadiCursor;
import org.zalando.nakadi.domain.TopicPartition;
import org.zalando.nakadi.exceptions.InvalidCursorException;
import org.zalando.nakadi.exceptions.NakadiException;

public interface EventConsumer extends Closeable {

    List<ConsumedEvent> readEvents();

    interface LowLevelConsumer extends EventConsumer {
        Set<TopicPartition> getAssignment();
    }

    interface ReassignableEventConsumer extends EventConsumer {
        Set<EventTypePartition> getAssignment();

        void reassign(Collection<NakadiCursor> newValues) throws NakadiException, InvalidCursorException;
    }
}
