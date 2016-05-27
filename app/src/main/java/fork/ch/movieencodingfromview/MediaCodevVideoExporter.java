package fork.ch.movieencodingfromview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by bmu on 31.03.16.
 */
public class MediaCodevVideoExporter {

    private static final int TEST_R0 = 0;                   // RGB equivalent of {0,0,0}
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // RGB equivalent of {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;

    private static final int TIMEOUT_USEC = 10000;
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAMERATE = 30;
    private static final long DUPLICATED_MEASUREMENT_FRAMES_PER_SECOND_IN_VIDEO = 8;
    private static final int BITRATE = 300000;
    private static final int AVERAGE_VARIABLE_BITRATE = 512000;
    private static final int CONSTANT_BITRATE = 1024000;
    private final Context context;
    private int mWidth;
    private int mHeight;
    private boolean mMuxerStarted;
    private int mTrackIndex;
    private MediaCodec mEncoder;
    private MediaCodecInfo mCodecInfo;
    private MediaCodec.BufferInfo mBufferInfo;
    private MediaMuxer mMuxer;
    private int mEncodingFrameCounter;
    private int mColorFormat;
    private InputSurface mInputSurface;
    private Bitmap mBitmap;

    public MediaCodevVideoExporter(Context context) {
        this.context = context;
    }


    @NonNull
    void createVideoUsingMediaCodec(View theView) throws IOException, InterruptedException {

        long start = System.currentTimeMillis();

        File file = new File(Environment.getExternalStorageDirectory(), "test.mp4");

        prepareEncoder(file, theView);

        mBitmap = Bitmap.createBitmap(theView.getWidth(),
                theView.getHeight(), Bitmap.Config.ARGB_8888);



        int measurementIndex = 0;
        Thread thread = startMuxerThread();

        // loop over all measurements
        for (measurementIndex = 0; measurementIndex < 5; measurementIndex++) {
            encodeMeasurementWithInputSurface(theView, measurementIndex);
        }


        mEncoder.signalEndOfInputStream();

        Timber.i("successfully queued all measurements for encoding");

        Timber.i("waiting for muxer to finish");
        thread.join();
        Timber.i("muxer thread finished");

        mBitmap.recycle();
    }

    private void prepareEncoder(File file, View theView) throws IOException {
        mCodecInfo = selectCodec(MIME_TYPE);
        boolean variableBitRateSupported = false;

        if (lollipopOrHigher()) {
            variableBitRateSupported = mCodecInfo.getCapabilitiesForType(MIME_TYPE)
                    .getEncoderCapabilities()
                    .isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        }


        if (mCodecInfo == null) {
            throw new IllegalStateException(
                    "unable to find a codec suitable for encoding " + MIME_TYPE);
        }
        Timber.d("found codec: %s", mCodecInfo.getName());

        mColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        Timber.d("surface : %d", mColorFormat);

        mWidth = theView.getWidth();
        mHeight = theView.getHeight();
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        if (variableBitRateSupported && lollipopOrHigher()) {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            format.setInteger(MediaFormat.KEY_BIT_RATE, AVERAGE_VARIABLE_BITRATE);
        } else {

            format.setInteger(MediaFormat.KEY_BIT_RATE, CONSTANT_BITRATE);

        }

        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAMERATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

        Timber.d("format: %s", format.toString());

        mEncodingFrameCounter = 0;
        mEncoder = MediaCodec.createByCodecName(mCodecInfo.getName());
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = new InputSurface(mEncoder.createInputSurface(), mWidth, mHeight);
        mEncoder.start();


        mBufferInfo = new MediaCodec.BufferInfo();

        mMuxer = new MediaMuxer(file.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    private Thread startMuxerThread() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                drainEncoder();
                releaseEncoder();

            }
        });
        thread.start();
        return thread;
    }

    private void encodeMeasurementWithInputSurface(
            View theView, int measurementIndex) {

        for (int i = 0; i < DUPLICATED_MEASUREMENT_FRAMES_PER_SECOND_IN_VIDEO; i++) {
            mInputSurface.makeCurrent();
            Canvas canvas1 = new Canvas(mBitmap);
            theView.draw(canvas1);

            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);

//
//            generateSurfaceFrame(measurementIndex);
            //loadBitmapAsTexture(mBitmap);
//            mInputSurface.drawFrame();
            RendererUtils.RenderContext program = RendererUtils.createProgram();
            int texture = RendererUtils.createTexture(mBitmap);
            RendererUtils.renderBackground(1,0,0);

            RendererUtils.renderTexture(program, texture, mWidth, mHeight);

//            if (mInputSurface.getSurface().isValid()) {
//                Canvas canvas = mInputSurface.getSurface().lockCanvas(new Rect(0, 0, mWidth, mHeight));
//                try {
//                    theView.draw(canvas);
//                } finally {
//                    if (canvas != null) {
//                        mInputSurface.getSurface().unlockCanvasAndPost(canvas);
//                    }
//                }
//            } else {
//                Timber.w("input surface not valid");
//            }


            mInputSurface.setPresentationTime(computePresentationTime() * 1000);
            mInputSurface.swapBuffers();
            mEncodingFrameCounter++;
            bitmap.recycle();
        }

        Timber.i("queued encoding frame %d", measurementIndex);


    }

    private void loadBitmapAsTexture(Bitmap bitmap) {
        int[] textures = new int[1];
        //Generate one texture pointer...
        GLES20.glGenTextures(1, textures, 0);


        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        //Create Nearest Filtered Texture
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        //Different possible texture parameters, e.g. GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);


        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);


    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no match was
     * found.
     */
    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {

                MediaCodecInfo.CodecCapabilities capabilitiesForType = codecInfo.getCapabilitiesForType(
                        types[j]);

                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }

        }
        return null;
    }

    private boolean lollipopOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no match is
     * found, this throws a test failure -- the set of formats known to the test should be expanded
     * for new platforms.
     */
    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0;   // not reached
    }

    private boolean drainEncoder() {
        final int TIMEOUT_USEC = 1000000;
        Timber.d("drainEncoder()");


        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Timber.d("no output available, spinning to await EOS");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Timber.d("mEncoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Timber.w("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Timber.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    Timber.d("sent " + mBufferInfo.size + " bytes to muxer");
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Timber.d("end of stream reached");
                    return true;      // out of while
                }
            }
        }
    }

    private void releaseEncoder() {
        Timber.d("releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime() {
        final long ONE_SECOND_IN_MICROSECONDS = 1000000;
        return mEncodingFrameCounter * (ONE_SECOND_IN_MICROSECONDS / DUPLICATED_MEASUREMENT_FRAMES_PER_SECOND_IN_VIDEO);
    }

    private void encodeMeasurementFrame(byte[] frameData, long presentationTime) {
        int inputBufIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        while (inputBufIndex != 0) {
            inputBufIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        }

        ByteBuffer inputBuffer = mEncoder.getInputBuffers()[inputBufIndex];
        inputBuffer.clear();
        inputBuffer.put(frameData);
        mEncoder.queueInputBuffer(inputBufIndex, 0, frameData.length, presentationTime, 0);
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how to
     * read and generate frames in this format).
     */
    private boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                Timber.d("COLOR_FormatYUV420Flexible");
                return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                Timber.d("COLOR_FormatYUV420Planar");
                return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                Timber.d("COLOR_FormatYUV420PackedPlanar");
                return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                Timber.d("COLOR_FormatYUV420SemiPlanar");
                return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                Timber.d("COLOR_FormatYUV420PackedSemiPlanar");
                return true;
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                Timber.d("COLOR_TI_FormatYUV420PackedSemiPlanar");
                return true;
            default:
                return false;
        }
    }

    private byte[] getFrameData(
            ViewGroup screenshotViewGroup, Bitmap bitmap) {


        Canvas canvas = new Canvas(bitmap);
        screenshotViewGroup.draw(canvas);
        canvas = null;

        int[] intArray = new int[mWidth * mWidth];
        bitmap.getPixels(intArray, 0, mWidth, 0, 0, mWidth, mHeight);

        return getYV12(mWidth, mHeight, bitmap);
    }

    private byte[] getYV12(int inputWidth, int inputHeight, Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYV12(yuv, argb, inputWidth, inputHeight);

        return yuv;
    }

    private void encodeYV12(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + (frameSize / 4);

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // YV12 has a plane of Y and two chroma plans (U, V) planes each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[vIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    /**
     * Generates a frame of data using GL commands.
     */
    private void generateSurfaceFrame(int frameIndex) {
        frameIndex %= 8;

        int startX, startY;
        if (frameIndex < 4) {
            // (0,0) is bottom-left in GL
            startX = frameIndex * (mWidth / 4);
            startY = mHeight / 2;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = 0;
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(TEST_R0 / 255.0f, TEST_G0 / 255.0f, TEST_B0 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, mWidth, mHeight);
        GLES20.glClearColor(TEST_R1 / 255.0f, TEST_G1 / 255.0f, TEST_B1 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);



    }


    // untested function
    private byte[] getNV21(int inputWidth, int inputHeight, Bitmap bitmap) {

        int[] argb = new int[inputWidth * inputHeight];

        bitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

        return yuv;
    }

    void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + ((yuv420sp.length - frameSize) / 2);


        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv420sp[vIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }

                index++;
            }
        }
    }

    /**
     * Generates data for frame N into the supplied buffer.  We have an 8-frame animation sequence
     * that wraps around.  It looks like this:
     * <pre>
     *   0 1 2 3
     *   7 6 5 4
     * </pre>
     * We draw one of the eight rectangles and leave the rest set to the zero-fill color.
     */
    private void generateFrame(int frameIndex, int colorFormat, byte[] frameData) {

        final int TEST_Y = 120;                  // YUV values for colored rect
        final int TEST_U = 160;
        final int TEST_V = 200;
        final int TEST_R0 = 0;                   // RGB equivalent of {0,0,0}
        final int TEST_G0 = 136;
        final int TEST_B0 = 0;
        final int TEST_R1 = 236;                 // RGB equivalent of {120,160,200}
        final int TEST_G1 = 50;
        final int TEST_B1 = 186;

        final int HALF_WIDTH = mWidth / 2;
        boolean semiPlanar = isSemiPlanarYUV(colorFormat);
        // Set to zero.  In YUV this is a dull green.
        Arrays.fill(frameData, (byte) 0);
        int startX, startY, countX, countY;
        frameIndex %= 8;
        //frameIndex = (frameIndex / 8) % 8;    // use this instead for debug -- easier to see
        if (frameIndex < 4) {
            startX = frameIndex * (mWidth / 4);
            startY = 0;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = mHeight / 2;
        }
        for (int y = startY + (mHeight / 2) - 1; y >= startY; --y) {
            for (int x = startX + (mWidth / 4) - 1; x >= startX; --x) {
                if (semiPlanar) {
                    // full-size Y, followed by UV pairs at half resolution
                    // e.g. Nexus 4 OMX.qcom.video.encoder.avc COLOR_FormatYUV420SemiPlanar
                    // e.g. Galaxy Nexus OMX.TI.DUCATI1.VIDEO.H264E
                    //        OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x] = (byte) TEST_U;
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x + 1] = (byte) TEST_V;
                    }
                } else {
                    // full-size Y, followed by quarter-size U and quarter-size V
                    // e.g. Nexus 10 OMX.Exynos.AVC.Encoder COLOR_FormatYUV420Planar
                    // e.g. Nexus 7 OMX.Nvidia.h264.encoder COLOR_FormatYUV420Planar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_U;
                        frameData[mWidth * mHeight + HALF_WIDTH * (mHeight / 2) +
                                (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_V;
                    }
                }
            }
        }
    }

    /**
     * Returns true if the specified color format is semi-planar YUV.  Throws an exception if the
     * color format is not recognized (e.g. not YUV).
     */
    private boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }


}
