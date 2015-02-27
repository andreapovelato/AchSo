package fi.aalto.legroup.achso.storage.local;

import com.squareup.otto.Bus;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import fi.aalto.legroup.achso.entities.Video;
import fi.aalto.legroup.achso.storage.VideoRepository;
import fi.aalto.legroup.achso.storage.VideoRepositoryUpdatedEvent;
import fi.aalto.legroup.achso.storage.formats.mp4.Mp4Reader;
import fi.aalto.legroup.achso.storage.formats.mp4.Mp4Writer;

public final class LocalVideoRepository extends AbstractLocalVideoRepository
        implements VideoRepository {

    private Bus bus;

    public LocalVideoRepository(Mp4Reader reader, Mp4Writer writer, File storageDirectory,
                                Bus bus) {
        super(reader, writer, storageDirectory);
        this.bus = bus;
    }

    /**
     * Returns an entity with the given ID.
     */
    @Override
    public Video get(UUID id) throws IOException {
        Video video = reader.read(getFile(id));

        video.setRepository(this);

        return video;
    }

    /**
     * Persists an entity with the given ID, overwriting a previous one if set.
     */
    @Override
    public void save(Video video) throws IOException {
        UUID id = video.getId();

        writer.write(video, getFile(id));

        bus.post(new VideoRepositoryUpdatedEvent(this));
    }

    /**
     * Deletes an entity with the given ID.
     */
    @Override
    public void delete(UUID id) throws IOException {
        File file = getFile(id);

        if (file.delete()) {
            bus.post(new VideoRepositoryUpdatedEvent(this));
        } else {
            throw new IOException("Could not delete " + file);
        }
    }

}