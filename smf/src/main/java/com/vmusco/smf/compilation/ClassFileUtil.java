package com.vmusco.smf.compilation;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * This class is directly taken from Spoon class loader project
 * Not used functions has been removed for simplicity
 * @see https://github.com/INRIA/spoon
 */
public class ClassFileUtil {

	static int u2(byte[] bytes, int offset) {
		int i = 0;
		i |= bytes[offset] & 0xFF;
		i <<= 8;
		i |= bytes[offset + 1] & 0xFF;
		return i;
	}

	public static void printBytes(byte[] bytes, int offset) {
		for (int i = offset; i < bytes.length; i++) {
			System.out.print("(" + i + "):" + bytes[i]);
		}
		System.out.println();
	}

	/**
	 * This method is required !!!
	 * @param generatePackagesStructure
	 * @param outputPath
	 * @param relativeFileName
	 * @param bytes
	 * @throws IOException
	 */
	public static void writeToDisk(boolean generatePackagesStructure,
			String outputPath, String relativeFileName, byte[] bytes)
			throws IOException {

		BufferedOutputStream output = null;
		if (generatePackagesStructure) {
			output = new BufferedOutputStream(new FileOutputStream(new File(
					buildAllDirectoriesInto(outputPath, relativeFileName))));
		} else {
			String fileName = null;
			char fileSeparatorChar = File.separatorChar;
			String fileSeparator = File.separator;
			// First we ensure that the outputPath exists
			outputPath = outputPath.replace('/', fileSeparatorChar);
			// To be able to pass the mkdirs() method we need to remove the
			// extra file separator at the end of the outDir name
			int indexOfPackageSeparator = relativeFileName
					.lastIndexOf(fileSeparatorChar);
			if (indexOfPackageSeparator == -1) {
				if (outputPath.endsWith(fileSeparator)) {
					fileName = outputPath + relativeFileName;
				} else {
					fileName = outputPath + fileSeparator + relativeFileName;
				}
			} else {
				int length = relativeFileName.length();
				if (outputPath.endsWith(fileSeparator)) {
					fileName = outputPath
							+ relativeFileName.substring(
									indexOfPackageSeparator + 1, length);
				} else {
					fileName = outputPath
							+ fileSeparator
							+ relativeFileName.substring(
									indexOfPackageSeparator + 1, length);
				}
			}
			output = new BufferedOutputStream(new FileOutputStream(new File(
					fileName)));
		}
		try {
			output.write(bytes, 0, bytes.length);
			// output.write(classFile.contents, 0, classFile.contentsOffset);
		} finally {
			output.flush();
			output.close();
		}
	}

	static String buildAllDirectoriesInto(String outputPath,
			String relativeFileName) throws IOException {
		char fileSeparatorChar = File.separatorChar;
		String fileSeparator = File.separator;
		File f;
		// First we ensure that the outputPath exists
		outputPath = outputPath.replace('/', fileSeparatorChar);
		// To be able to pass the mkdirs() method we need to remove the extra
		// file separator at the end of the outDir name
		if (outputPath.endsWith(fileSeparator)) {
			outputPath = outputPath.substring(0, outputPath.length() - 1);
		}
		f = new File(outputPath);
		if (f.exists()) {
			if (!f.isDirectory()) {
				final String message = Messages.bind(Messages.output_isFile, f
						.getAbsolutePath());
				throw new IOException(message);
			}
		} else {
			// we have to create that directory
			if (!f.mkdirs()) {
				final String message = Messages.bind(
						Messages.output_notValidAll, f.getAbsolutePath());
				throw new IOException(message);
			}
		}
		StringBuffer outDir = new StringBuffer(outputPath);
		outDir.append(fileSeparator);
		StringTokenizer tokenizer = new StringTokenizer(relativeFileName,
				fileSeparator);
		String token = tokenizer.nextToken();
		while (tokenizer.hasMoreTokens()) {
			f = new File(outDir.append(token).append(fileSeparator).toString());
			if (f.exists()) {
				// The outDir already exists, so we proceed the next entry
				// System.out.println("outDir: " + outDir + " already exists.");
			} else {
				// Need to add the outDir
				if (!f.mkdir()) {
					throw new IOException(Messages.bind(
							Messages.output_notValid, f.getName()));
				}
			}
			token = tokenizer.nextToken();
		}
		// token contains the last one
		return outDir.append(token).toString();
	}

}