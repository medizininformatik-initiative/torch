package de.medizininformatikinitiative.flare.model;

import de.medizininformatikinitiative.flare.service.SerializerException;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

/**
 * A Population is an immutable set of patient ids.
 * <p>
 * Patient ids can have a maximum length of 64 chars.
 * <p>
 * Uses a sorted array of patient ids internally to store the patient ids memory efficient and facilitate fast
 * operations like {@link #intersection(Population) intersection}, {@link #union(Population) union} and
 * {@link #difference(Population) difference}.
 * <p>
 * Note: {@link #contains(Object) contains} is O(n)
 */
public final class Population extends AbstractSet<String> {

    private static final Population EMPTY = new Population(new String[0], Instant.EPOCH);

    private final String[] patientIds;
    private final Instant created;

    private Population(String[] patientIds, Instant created) {
        this.patientIds = patientIds;
        this.created = created;
    }

    public static Population of() {
        return EMPTY;
    }

    public static Population of(String patientId1) {
        checkPatientId(patientId1);
        return new Population(new String[]{patientId1.intern()}, Instant.EPOCH);
    }

    /**
     * Returns an immutable population containing two patient ids.
     *
     * @param patientId1 the first patient id
     * @param patientId2 the second patient id
     * @return a {@code Population} containing the specified patient ids
     * @throws IllegalArgumentException if one of the patient ids is longer than 64 chars or if the patient ids are
     *                                  duplicates
     * @throws NullPointerException     if a patient id is {@code null}
     */
    public static Population of(String patientId1, String patientId2) {
        checkPatientId(patientId1);
        checkPatientId(patientId2);
        var id1 = patientId1.intern();
        var id2 = patientId2.intern();
        if (id1.equals(id2)) {
            throw new IllegalArgumentException("duplicate patient id: " + id1);
        }
        return new Population(id1.compareTo(id2) < 0 ? new String[]{id1, id2} : new String[]{id2, id1}, Instant.EPOCH);
    }

    public static Population copyOf(Collection<String> patientIds) {
        var idSet = Set.copyOf(patientIds);
        String[] internedIds = new String[idSet.size()];
        int i = 0;
        for (String id : idSet) {
            checkPatientId(id);
            internedIds[i++] = id.intern();
        }
        Arrays.sort(internedIds);
        return new Population(internedIds, Instant.EPOCH);
    }

    /**
     * Returns the instant at which this population was created.
     * <p>
     * Usually the created instant is used for cache expiry, but can be also used to determine how old a population is.
     *
     * @return the instant at which this population was created
     */
    public Instant created() {
        return created;
    }

    /**
     * Returns a new population with {@code created} set to the given value.
     *
     * @param created the instant to use as created instant for the new population
     * @return a new population with {@code created} set to the given value
     */
    public Population withCreated(Instant created) {
        return new Population(patientIds, requireNonNull(created));
    }

    @Override
    public boolean isEmpty() {
        return patientIds.length == 0;
    }

    @Override
    public Iterator<String> iterator() {
        return new PatientIdIterator();
    }

    @Override
    public int size() {
        return patientIds.length;
    }

    /**
     * Returns the size of this population in memory.
     * <p>
     * The calculation uses the fact that an array is used to store the patient ids internally. In addition it assumes
     * that all patient ids are {@link String#intern() interned}. Each entry is a reference to a string. References
     * have a size of 4 bytes.
     * <p>
     * So the memory size is 24 bytes for the population class, 24 bytes for the instant, 20 bytes for the array and 4
     * times the size of the array for the array holding the string references. The strings itself do not count, because
     * they are interned.
     *
     * @return the size of this population in memory
     */
    public int memSize() {
        return 68 + patientIds.length * 4;
    }

    public Population intersection(Population other) {
        int i = 0, j = 0, r = 0;
        var res = new String[Math.min(patientIds.length, other.patientIds.length)];

        while (i < patientIds.length && j < other.patientIds.length) {
            int cmp = patientIds[i].compareTo(other.patientIds[j]);
            if (cmp < 0) {
                i++;
            } else if (cmp > 0) {
                j++;
            } else {
                res[r++] = patientIds[i++];
                j++;
            }
        }

        return new Population(Arrays.copyOf(res, r), created.isBefore(other.created) ? created : other.created);
    }

    public Population union(Population other) {
        int i = 0, j = 0, r = 0;
        var res = new String[patientIds.length + other.patientIds.length];

        while (i < patientIds.length && j < other.patientIds.length) {
            int cmp = patientIds[i].compareTo(other.patientIds[j]);
            if (cmp < 0) {
                res[r++] = patientIds[i++];
            } else if (cmp > 0) {
                res[r++] = other.patientIds[j++];
            } else {
                res[r++] = patientIds[i++];
                j++;
            }
        }

        while (i < patientIds.length) {
            res[r++] = patientIds[i++];
        }

        while (j < other.patientIds.length) {
            res[r++] = other.patientIds[j++];
        }

        return new Population(Arrays.copyOf(res, r), created.isBefore(other.created) ? created : other.created);
    }

    public Population difference(Population other) {
        int i = 0, j = 0, r = 0;
        var res = new String[patientIds.length];

        while (i < patientIds.length && j < other.patientIds.length) {
            int cmp = patientIds[i].compareTo(other.patientIds[j]);
            if (cmp < 0) {
                res[r++] = patientIds[i++];
            } else if (cmp > 0) {
                j++;
            } else {
                i++;
                j++;
            }
        }

        while (i < patientIds.length) {
            res[r++] = patientIds[i++];
        }

        return new Population(Arrays.copyOf(res, r), created.isBefore(other.created) ? created : other.created);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Population p))
            return false;

        return created.equals(p.created) && Arrays.equals(patientIds, p.patientIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(created, Arrays.hashCode(patientIds));
    }

    @Override
    public String toString() {
        return "Population[size=" + size() + "]";
    }

    public ByteBuffer toByteBuffer() {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(serializedSize());
        byteBuffer.put((byte) 0); //version byte

        byteBuffer.putLong(created.getEpochSecond());
        byteBuffer.putInt(patientIds.length);

        for (String id : patientIds) {
            byte[] bytes = id.getBytes(US_ASCII);
            byteBuffer.put((byte) bytes.length);
            byteBuffer.put(bytes);
        }

        return byteBuffer.flip();
    }

    private int serializedSize() {
        return Arrays.stream(patientIds).mapToInt(id -> id.getBytes(US_ASCII).length + 1).sum() + 13;
    }

    public static Population fromByteBuffer(ByteBuffer byteBuffer) throws SerializerException {
        if (!byteBuffer.hasRemaining()) {
            throw new SerializerException("Can't deserialize an empty byte buffer.");
        }

        byte versionByte = byteBuffer.get();
        if (versionByte != 0) {
            throw new SerializerException("Can't deserialize because version %d isn't supported.".formatted(versionByte));
        }

        var created = Instant.ofEpochSecond(byteBuffer.getLong());

        var patientIds = new String[byteBuffer.getInt()];
        int i = 0;
        while (i < patientIds.length) {
            byte[] idBytes = new byte[byteBuffer.get()];
            byteBuffer.get(idBytes);
            patientIds[i++] = new String(idBytes, US_ASCII).intern();
        }
        return new Population(patientIds, created);
    }

    private static void checkPatientId(String patientId1) {
        if (patientId1.length() > 64) {
            throw new IllegalArgumentException("Patient id `%s` is longer as 64 chars.".formatted(patientId1));
        }
    }

    private class PatientIdIterator implements Iterator<String> {

        private int cursor;

        @Override
        public boolean hasNext() {
            return cursor != patientIds.length;
        }

        @Override
        public String next() {
            try {
                return patientIds[cursor++];
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
    }
}
