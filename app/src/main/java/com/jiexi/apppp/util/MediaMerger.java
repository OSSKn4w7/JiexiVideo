package com.jiexi.apppp.util;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaMerger {

    /**
     * Merge video-only and audio-only files into a single MP4 with audio+video.
     * @return true on success, false on failure. Output file is created at outputPath.
     */
    public static boolean merge(String videoPath, String audioPath, String outputPath) {
        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;
        MediaMuxer muxer = null;

        try {
            File videoFile = new File(videoPath);
            File audioFile = new File(audioPath);
            if (!videoFile.exists() || !audioFile.exists()) return false;

            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoPath);

            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audioPath);

            int videoTrack = selectTrack(videoExtractor, "video/");
            int audioTrack = selectTrack(audioExtractor, "audio/");
            if (videoTrack < 0 || audioTrack < 0) return false;

            videoExtractor.selectTrack(videoTrack);
            audioExtractor.selectTrack(audioTrack);

            MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrack);
            MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrack);

            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoMuxIndex = muxer.addTrack(videoFormat);
            int audioMuxIndex = muxer.addTrack(audioFormat);

            muxer.start();

            // Copy video
            copyTrack(videoExtractor, muxer, videoMuxIndex);
            // Copy audio
            copyTrack(audioExtractor, muxer, audioMuxIndex);

            muxer.stop();

            return new File(outputPath).exists();

        } catch (Exception e) {
            Logger.e("Merger", "合并失败: " + outputPath, e);
            return false;
        } finally {
            if (videoExtractor != null) {
                try { videoExtractor.release(); } catch (Exception ignored) {}
            }
            if (audioExtractor != null) {
                try { audioExtractor.release(); } catch (Exception ignored) {}
            }
            if (muxer != null) {
                try { muxer.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static int selectTrack(MediaExtractor extractor, String mimePrefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(mimePrefix)) {
                return i;
            }
        }
        return -1;
    }

    private static void copyTrack(MediaExtractor extractor, MediaMuxer muxer,
                                   int trackIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        // Adjust sample rate for audio if needed (some decoders are picky)
        long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                ? format.getLong(MediaFormat.KEY_DURATION) : Long.MAX_VALUE;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long ptsOffset = -1;
        long startTime = System.currentTimeMillis();

        while (true) {
            try {
                info.size = extractor.readSampleData(buffer, 0);
                if (info.size < 0) break;

                info.offset = 0;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = extractor.getSampleFlags();

                // Align presentation timestamps between tracks
                if (ptsOffset < 0) {
                    ptsOffset = info.presentationTimeUs;
                }
                info.presentationTimeUs -= ptsOffset;

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // Some muxers reject CODEC_CONFIG frames; skip them
                    extractor.advance();
                    continue;
                }

                muxer.writeSampleData(trackIndex, buffer, info);
                extractor.advance();

            } catch (Exception e) {
                break;
            }
        }
    }
}
