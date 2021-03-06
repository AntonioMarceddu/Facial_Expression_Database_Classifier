package it.polito.fedc.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import it.polito.fedc.Controller;
import javafx.application.Platform;

public class UnzipClass 
{
	/* Method for decompressing a zip file. */
	public void unzip(Controller controller, String zipFile, String destinationDir) throws IOException 
	{
		long total = 0, uncompressed = 0;
		double percentage = 0, difference = 0;

		File destDir = new File(destinationDir);
		if (!destDir.exists()) 
		{
			destDir.mkdir();
		}

		// Calculate the actual files size, without compression.
		ZipFile zf = new ZipFile(zipFile);
		Enumeration<? extends ZipEntry> e = zf.entries();
		while (e.hasMoreElements()) 
		{
			ZipEntry ze = (ZipEntry) e.nextElement();
			total = total + ze.getSize();
		}
		zf.close();

		FileInputStream fis = new FileInputStream(zipFile);
		ZipInputStream zis = new ZipInputStream(fis);
		ZipEntry entry;
		// Iteration on the zip file entries.
		while (((entry = zis.getNextEntry()) != null) && (!Thread.currentThread().isInterrupted())) 
		{
			String filePath = destDir + File.separator + entry.getName();

			// Protection against directory with a ":" in the name for Windows: could happen with FACES Database.
			if (filePath.substring(3).contains(":")) 
			{
				filePath = filePath.substring(0, 3) + filePath.substring(3).replace(":", "_");
			}

			// Case where the entry is a directory.
			if (entry.isDirectory()) 
			{
				File dir = new File(filePath);
				dir.mkdirs();
			}
			// Case where the entry is a file.
			else 
			{
				// Create theparent directories.
				File parent = new File(filePath).getParentFile();
				parent.mkdirs();

				// Reading from entry and writing to file.
				FileOutputStream fos = new FileOutputStream(filePath);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				byte[] bytesToRead = new byte[8192];
				int read = 0;
				while ((read = zis.read(bytesToRead)) != -1) 
				{
					uncompressed = uncompressed + (long) read;
					bos.write(bytesToRead, 0, read);
				}
				bos.close();
				fos.close();

				// Calculate the percentage of completion of the current operation and update of the unarchiving progress bar.
				percentage = (double) uncompressed / (double) total;
				if (percentage - difference >= 0.01) 
				{
					difference = difference + (double) 0.01;
					difference = Math.round(difference * 100);
					difference = difference / 100;

					final double value = percentage;
					Platform.runLater(() -> controller.updateProgressBar(value));
				}
			}
			zis.closeEntry();
		}
		// Closure of the entry in case of early termination.
		if (entry != null) 
		{
			zis.closeEntry();
		}
		zis.close();
		fis.close();
	}
}