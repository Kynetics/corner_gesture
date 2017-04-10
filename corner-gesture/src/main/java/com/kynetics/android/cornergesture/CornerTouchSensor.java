package com.kynetics.android.cornergesture;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.kynetics.android.timeout.TimeoutTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

/**
 * Created by andrea on 22/12/16.
 */
public class CornerTouchSensor extends GestureDetector.SimpleOnGestureListener {

    public interface CornerTouchSequenceListener {
        void cornerTouchSequenceInserted(String sequence);
    }

    public interface CornerTouchSequenceController {
        void enableCornerTouchSensor();
        void disableCornerTouchSensor();
    }

    public static class Builder {

        public static final int MIN_CORNER_SIZE = 20;

        public static final long MIN_RESET_TIMEOUT = 1000L;

        private static final String SEQUENCE_REGEX = "([TB][LR]){3,}";

        private static final Pattern SEQUENCE_PATTERN = Pattern.compile(SEQUENCE_REGEX);

        private Builder() {}

        public Builder withContext(Context context) {
            this.context = context;
            return this;
        }

        public Builder withResetTimeout(long resetTimeout) {
            this.resetTimeout = resetTimeout;
            return this;
        }

        public Builder withCornerSize(int cornerSize) {
            this.cornerSize = cornerSize;
            return this;
        }

        public Builder withListener(CornerTouchSequenceListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder withSequences(String...sequences) {
            this.sequences.addAll(Arrays.asList(sequences)) ;
            return this;
        }

        public Builder withSequence(String sequence) {
            this.sequences.add(sequence);
            return this;
        }

        public CornerTouchSensor build() {
            assertNotNull("context", context);
            assertNotNull("listener", listener);
            assertGreater("resetTimeout", resetTimeout, MIN_RESET_TIMEOUT);
            assertGreater("cornerSize", cornerSize, MIN_CORNER_SIZE);
            assertValidSequences();
            return new CornerTouchSensor(context, resetTimeout, cornerSize, listener, sequences);
        }

        private void assertNotNull(String fieldName, Object field) {
            if(field == null) {
                throw new IllegalArgumentException(String.format("The %s attribute must be set.", fieldName));
            }
        }

        private void assertGreater(String fieldName, long field, long threshold) {
            if(field < threshold) {
                throw new IllegalArgumentException(String.format("The %s attribute must be greater than %s", fieldName, threshold));
            }
        }

        private void assertValidSequences() {
            if(sequences.isEmpty()) {
                throw new IllegalArgumentException("The corner sequence can not be empty.");
            }
            final String[] sequences = this.sequences.toArray(new String[this.sequences.size()]);
            for(int i=0; i < sequences.length; i++) {
                final String sequence = sequences[i];
                if(!SEQUENCE_PATTERN.matcher(sequence).matches()) {
                    throw new IllegalArgumentException(String.format("Illegal sequence syntax. The sequence %s does not match the allowed regexp %s", sequence, SEQUENCE_REGEX));
                }
                for(int j=0; j<i; j++) {
                    final String prevSequence = sequences[j];
                    if(prevSequence.startsWith(sequence) || sequence.startsWith(prevSequence)) {
                        throw new IllegalArgumentException("The following sequences can not be distinguished: "+sequence+" and "+prevSequence);
                    }
                }
            }
        }

        private Context context;
        private CornerTouchSequenceListener listener;
        private long resetTimeout = MIN_RESET_TIMEOUT;
        private int cornerSize = MIN_CORNER_SIZE;
        private final Set<String> sequences = new HashSet<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean processMotionEvent(MotionEvent motionEvent) {
        if(!enabled) {
            return false;
        }
        final int action = motionEvent.getActionMasked();
        this.corner = findCorner((int) motionEvent.getX(), (int) motionEvent.getY());
        if(action == ACTION_DOWN && this.corner != null) {
            this.armed = true;
        }
        if(armed) {
            this.gestureDetector.onTouchEvent(motionEvent);
            if(action == ACTION_UP || action == ACTION_CANCEL) {
                armed = false;
            }
            return true;
        }
        return false;
    }

    @Override
    public final boolean onSingleTapUp(MotionEvent motionEvent) {
        if (corner != null) {
            resetTask.start();
            onCornerPressed(corner);
        } else {
            currentCornerSequence = "";
            resetTask.stop();
        }
        return true;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
        this.resetTask.stop();
    }

    private CornerTouchSensor(Context context, long timeout, int size, CornerTouchSequenceListener listener, Set<String> sequences) {
        this.listener = listener;
        this.sequences = Collections.unmodifiableSet(sequences);
        this.gestureDetector = new GestureDetector(context, this);
        this.gestureDetector.setOnDoubleTapListener(null);
        this.resetTask = TimeoutTask.newInstance(timeout, new Runnable() {
            @Override
            public void run() {
                currentCornerSequence = "";
            }
        });
        this.cornerSize = size;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        this.enable();
    }

    private void onCornerPressed(String corner) {
        resetTask.start();
        currentCornerSequence += corner;
        boolean canMatch = false;
        for(String sequence : sequences) {
            if(sequence.equals(currentCornerSequence)) {
                listener.cornerTouchSequenceInserted(sequence);
                currentCornerSequence = "";
                resetTask.stop();
                break;
            } else if(sequence.startsWith(currentCornerSequence)) {
                canMatch = true;
                resetTask.delay();
                break;
            }
        }
        if(!canMatch) {
            currentCornerSequence = "";
        }
    }

    private String findCorner(int x, int y) {
        if(x <= cornerSize && y <= cornerSize ) return "TL";
        if(x <= cornerSize && y > height - cornerSize) return "BL";
        if(x > width - cornerSize && y <= cornerSize) return "TR";
        if(x > width - cornerSize && y > height - cornerSize) return "BR";
        return null;
    }

    private String currentCornerSequence = "";

    private boolean enabled = true;

    private boolean armed = false;

    private String corner = null;

    private final GestureDetector gestureDetector;

    private final Set<String> sequences;

    private final CornerTouchSequenceListener listener;

    private final int width;// = Math.round(BuildConfig.WINDOW_HEIGHT * 1.6f);

    private  final int height;// = BuildConfig.WINDOW_HEIGHT;

    private final TimeoutTask resetTask;

    private final int cornerSize;

}