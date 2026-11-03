package com.babplanner.backend.dev.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * 시연용 잔반 샘플 이미지(PNG).
 *
 * <p>java.desktop(AWT)이나 글꼴에 의존하지 않도록 PNG를 직접 인코딩한다. 식판과 남은
 * 음식을 단순한 도형으로만 그리며, 사람이나 글자, 실제 사진은 들어가지 않는다.
 * 남은 음식의 양이 {@code wasteRatio}에 비례한다.
 */
final class DemoPng {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;

    private DemoPng() {}

    static byte[] generate(long seed, double wasteRatio) {
        Random random = new Random(seed);
        int[][] pixels = new int[HEIGHT][WIDTH];
        int background = rgb(236 + random.nextInt(8), 232 + random.nextInt(8), 224);
        for (int[] row : pixels) {
            java.util.Arrays.fill(row, background);
        }
        // 식판
        fillRoundedRect(pixels, 14, 14, WIDTH - 15, HEIGHT - 15, rgb(120, 126, 134));
        fillRoundedRect(pixels, 20, 20, WIDTH - 21, HEIGHT - 21, rgb(168, 174, 182));
        // 칸막이
        fillRect(pixels, 118, 20, 121, HEIGHT - 21, rgb(120, 126, 134));
        fillRect(pixels, 20, 84, 117, 87, rgb(120, 126, 134));

        double ratio = Math.max(0.0, Math.min(1.0, wasteRatio));
        int[][] centers = {{70, 52, 28}, {70, 120, 26}, {170, 52, 30}, {170, 118, 28}};
        int[] colors = {
            rgb(244, 236, 208), rgb(98, 148, 76), rgb(176, 96, 52), rgb(214, 170, 70)
        };
        for (int i = 0; i < centers.length; i++) {
            int radius = (int) Math.round(centers[i][2] * (0.25 + 0.75 * ratio));
            if (ratio < 0.04) {
                continue;
            }
            fillCircle(pixels, centers[i][0], centers[i][1], radius, colors[i]);
        }
        return encode(pixels);
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private static void fillRect(int[][] p, int x0, int y0, int x1, int y1, int color) {
        for (int y = Math.max(0, y0); y <= Math.min(HEIGHT - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(WIDTH - 1, x1); x++) {
                p[y][x] = color;
            }
        }
    }

    private static void fillRoundedRect(int[][] p, int x0, int y0, int x1, int y1, int color) {
        fillRect(p, x0 + 4, y0, x1 - 4, y1, color);
        fillRect(p, x0, y0 + 4, x1, y1 - 4, color);
    }

    private static void fillCircle(int[][] p, int cx, int cy, int radius, int color) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (y < 0 || y >= HEIGHT || x < 0 || x >= WIDTH) {
                    continue;
                }
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) {
                    p[y][x] = color;
                }
            }
        }
    }

    private static byte[] encode(int[][] pixels) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(raw)) {
                for (int[] row : pixels) {
                    deflate.write(0); // filter: none
                    for (int value : row) {
                        deflate.write((value >> 16) & 0xFF);
                        deflate.write((value >> 8) & 0xFF);
                        deflate.write(value & 0xFF);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            byte[] header = new byte[13];
            putInt(header, 0, WIDTH);
            putInt(header, 4, HEIGHT);
            header[8] = 8; // bit depth
            header[9] = 2; // RGB
            chunk(out, "IHDR", header);
            chunk(out, "IDAT", raw.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("샘플 이미지를 만들지 못했습니다.", impossible);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data)
            throws IOException {
        byte[] length = new byte[4];
        putInt(length, 0, data.length);
        out.write(length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        putInt(crcBytes, 0, (int) crc.getValue());
        out.write(crcBytes);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
