package com.avatar.tla;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class IsoExtractor {
    public interface ProgressCallback {
        void onProgress(int percent);
    }

    public static boolean extractFile(InputStream isoStream, long isoSize, File outFile, String targetName, ProgressCallback cb) throws IOException {
        // Copy stream to a temp file for random access if needed? RandomAccessFile needs a file.
        // We'll write to a temp file first for seeking.
        File tmp = new File(outFile.getParentFile(), "._iso_tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            long copied = 0;
            int n;
            while ((n = isoStream.read(buf)) > 0) {
                os.write(buf, 0, n);
                copied += n;
                if (isoSize > 0 && cb != null) {
                    cb.onProgress((int) Math.min(50, (copied * 50) / isoSize)); // 0-50% copy
                }
            }
        }
        try {
            extractFileFromIsoFile(tmp, outFile, targetName, cb, 50, 50);
            if (cb != null) cb.onProgress(100);
            return true;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Extract a file from an existing ISO file on disk. progressBase/progressSpan map extraction progress into cb range. */
    public static boolean extractFileFromIsoFile(File isoFile, File outFile, String targetName, ProgressCallback cb, int progressBase, int progressSpan) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(isoFile, "r")) {
            // Primary Volume Descriptor at sector 16
            byte[] pvd = readSector(raf, 16);
            if (pvd[0] != 1 || pvd[1] != 'C' || pvd[2] != 'D' || pvd[3] != '0' || pvd[4] != '0' || pvd[5] != '1') {
                throw new IOException("Not a valid ISO9660 image");
            }
            // Root directory record at offset 156 in PVD (34 bytes)
            int rootExtent = le32(pvd, 156 + 2);
            int rootSize = le32(pvd, 156 + 10);
            int[] found = findFile(raf, rootExtent, rootSize, targetName.toUpperCase());
            if (found == null) {
                // try with common variations
                found = findFile(raf, rootExtent, rootSize, targetName.toUpperCase().replace('.', '_'));
                if (found == null) found = findFile(raf, rootExtent, rootSize, targetName.toUpperCase().replace('_', '.'));
            }
            if (found == null) throw new IOException("File not found in ISO: " + targetName);
            int extent = found[0];
            int size = found[1];
            if (cb != null) cb.onProgress(progressBase + (int)(progressSpan * 0.10));
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[65536];
                long remaining = size;
                long pos = (long) extent * 2048L;
                raf.seek(pos);
                long written = 0;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int r = raf.read(buf, 0, toRead);
                    if (r <= 0) break;
                    fos.write(buf, 0, r);
                    remaining -= r;
                    written += r;
                    if (size > 0 && cb != null) {
                        int p = progressBase + (int)(progressSpan * 0.10) + (int) ((written * (progressSpan * 0.90)) / size);
                        cb.onProgress(Math.min(progressBase + progressSpan, p));
                    }
                }
            }
            return true;
        }
    }

    private static int[] findFile(RandomAccessFile raf, int dirExtent, int dirSize, String targetUpper) throws IOException {
        int sectors = (dirSize + 2047) / 2048;
        byte[] data = new byte[sectors * 2048];
        raf.seek((long) dirExtent * 2048L);
        raf.readFully(data, 0, Math.min(data.length, dirSize));
        int offset = 0;
        while (offset < dirSize) {
            int len = data[offset] & 0xFF;
            if (len == 0) {
                // move to next sector boundary
                int next = ((offset / 2048) + 1) * 2048;
                if (next <= offset) break;
                offset = next;
                continue;
            }
            if (offset + len > data.length) break;
            int flags = data[offset + 25] & 0xFF;
            int nameLen = data[offset + 32] & 0xFF;
            if (nameLen > 0 && offset + 33 + nameLen <= data.length) {
                String name = new String(data, offset + 33, nameLen, "ISO-8859-1");
                String clean = name;
                int semi = clean.indexOf(';');
                if (semi >= 0) clean = clean.substring(0, semi);
                clean = clean.toUpperCase();
                boolean isDir = (flags & 0x02) != 0;
                if (!isDir && clean.equals(targetUpper)) {
                    int ext = le32(data, offset + 2);
                    int size = le32(data, offset + 10);
                    return new int[]{ext, size};
                }
                if (isDir && nameLen != 1) { // skip . and ..
                    int childExt = le32(data, offset + 2);
                    int childSize = le32(data, offset + 10);
                    int[] r = findFile(raf, childExt, childSize, targetUpper);
                    if (r != null) return r;
                }
            }
            offset += len;
        }
        return null;
    }

    private static byte[] readSector(RandomAccessFile raf, int sector) throws IOException {
        byte[] buf = new byte[2048];
        raf.seek((long) sector * 2048L);
        raf.readFully(buf);
        return buf;
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
}
