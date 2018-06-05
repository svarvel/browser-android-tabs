// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android.commands.unzip;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *  Minimal implementation of the command-line unzip utility for Android.
 */
public class Unzip {

    private static final String TAG = "Unzip";

    public static void main(String[] args) {
        try {
            (new Unzip()).run(args);
        } catch (RuntimeException e) {
            Log.e(TAG, e.toString());
            System.exit(1);
        }
    }

    private void showUsage(PrintStream s) {
        s.println("Usage:");
        s.println("unzip [zipfile]");
    }

    private String validateFilename(String filename, String intendedDir)
      throws java.io.IOException {
  	File f = new File(intendedDir, filename);
  	String canonicalPath = f.getCanonicalPath();

  	File iD = new File(intendedDir);
  	String canonicalID = iD.getCanonicalPath();

  	if (canonicalPath.startsWith(canonicalID)) {
    	    return filename;
  	} else {
    	    throw new IllegalStateException("File is outside extraction target directory.");
        }
    }

    @SuppressWarnings("Finally")
    private void unzip(String[] args) {
        ZipInputStream zis = null;
        try {
            String zipfile = args[0];
            zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipfile)));
            ZipEntry ze = null;

            byte[] bytes = new byte[1024];
            while ((ze = zis.getNextEntry()) != null) {
                String entry = validateFilename(ze.getName(), ".");
                File outputFile = new File(entry);
                if (ze.isDirectory()) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw new RuntimeException(
                                "Failed to create directory: " + outputFile.toString());
                    }
                } else {
                    File parentDir = outputFile.getParentFile();
                    if (!parentDir.exists() && !parentDir.mkdirs()) {
                        throw new RuntimeException(
                                "Failed to create directory: " + parentDir.toString());
                    }
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile));
                    int actual_bytes = 0;
                    int total_bytes = 0;
                    while ((actual_bytes = zis.read(bytes)) != -1) {
                        out.write(bytes, 0, actual_bytes);
                        total_bytes += actual_bytes;
                    }
                    out.close();
                }
                zis.closeEntry();
            }

        } catch (IOException e) {
            throw new RuntimeException("Error while unzipping: " + e.toString());
        } catch (IllegalStateException e) {
            throw new RuntimeException("Error while unzipping: " + e.toString());
        } finally {
            try {
                if (zis != null) zis.close();
            } catch (IOException e) {
                throw new RuntimeException("Error while closing zip: " + e.toString());
            }
        }
    }

    public void run(String[] args) {
        if (args.length != 1) {
            showUsage(System.err);
            throw new RuntimeException("Incorrect usage!");
        }

        unzip(args);
    }
}
