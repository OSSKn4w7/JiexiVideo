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

            // Check codec compatibility — MediaMuxer only reliably supports AVC+AAC
            String videoMime = videoFormat.getString(MediaFormat.KEY_MIME);
            String audioMime = audioFormat.getString(MediaFormat.KEY_MIME);
            Logger.i("Merger", "视频编码=" + videoMime + " 音频编码=" + audioMime);
            if (!"video/avc".equals(videoMime) && !"video/mp4".equals(videoMime)) {
                Logger.i("Merger", "跳过合并不支持的视频编码: " + videoMime);
                return false;
            }

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
                                   int muxerTrackIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long ptsOffset = -1;

        while (true) {
            info.size = extractor.readSampleData(buffer, 0);
            if (info.size < 0) break;

            info.offset = 0;
            info.presentationTimeUs = extractor.getSampleTime();
            info.flags = extractor.getSampleFlags();

            if (ptsOffset < 0) {
                ptsOffset = info.presentationTimeUs;
            }
            info.presentationTimeUs -= ptsOffset;

            // Skip config frames — some muxers reject them
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                extractor.advance();
                continue;
            }

            try {
                muxer.writeSampleData(muxerTrackIndex, buffer, info);
            } catch (Exception e) {
                // Muxer might reject some frames; skip and continue
            }
            extractor.advance();
        }
    }
}
