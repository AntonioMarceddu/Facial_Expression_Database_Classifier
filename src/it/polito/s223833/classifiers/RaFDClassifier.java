package it.polito.s223833.classifiers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javafx.application.Platform;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import it.polito.s223833.MainController;
import it.polito.s223833.utils.UnzipClass;


public class RaFDClassifier extends Classifier implements Runnable 
{
	private CascadeClassifier profileFaceCascade;
	private boolean profile, subdivision;
	private double trainPercentage, validationPercentage, testPercentage;

	public RaFDClassifier(MainController controller, String inputFile, String outputDirectory, int width, int height, boolean grayscale, boolean histogramEqualization, boolean faceDetection, boolean profile, boolean subdivision, double trainPercentage, double validationPercentage, double testPercentage)
	{
		super(controller, inputFile, outputDirectory, true, width, height, grayscale, histogramEqualization, faceDetection);
		absoluteFaceSize = 400;
		this.subdivision = subdivision;
		this.trainPercentage = trainPercentage;
		this.validationPercentage = validationPercentage;
		this.testPercentage = testPercentage;
		this.profile = profile;
		profileFaceCascade = new CascadeClassifier(haarclassifierpath + "haarcascade_profileface.xml");
	}

	@Override
	public void run() 
	{
		// Extraction of the images of the RaFD database.
		UnzipClass unzipper = new UnzipClass();
		try 
		{
			Platform.runLater(() -> controller.setPhaseLabel("Phase 1: extraction of the RaFD images..."));
			unzipper.Unzip(controller, inputFile, tempDirectory);
		} 
		catch (IOException e1) 
		{
			ExceptionManager("There was an error during the extraction of the RaFD files.");
			return;
		}
		
		// Verifies if the user has requested the cancellation of the current operation during the extraction phase.
		if(!Thread.currentThread().isInterrupted())
		{
			// Creation of classification folders for the RaFD database.
			try 
			{
				CreateFolders();
			} 
			catch (SecurityException e) 
			{
				ExceptionManager("There was a problem while creating classification folders.");
		    	return;
			}
			
			Platform.runLater(() -> controller.setPhaseLabel("Phase 2: classification..."));
	
			// Reading the newly extracted photos.
			File rafdImages = new File(tempDirectory);
			File[] listOfFiles = rafdImages.listFiles();
			// Cycle performed for every single file in the folder.
			int i = 0, numberOfFiles = listOfFiles.length;
			boolean faceFound;
			while ((i < numberOfFiles) && (!Thread.currentThread().isInterrupted()))
			{
				File file = listOfFiles[i];
				
				// Verifies that the filename has the typical form of the RaFD database files.
				if ((file.isFile()) && (file.getName().matches("Rafd[0-9]{3}_[0-9]{2}_[A-Z][a-z]*_[a-z]*_[a-z]*_[a-z]*\\.jpg"))) 
				{
					faceFound = true;
					Mat resizedFace = Mat.zeros(imageSize, CvType.CV_8UC1);
	
					// Opening the image to be analyzed.
					Mat image = Imgcodecs.imread(file.getAbsolutePath());
					// Conversion of the image in grayscale (optional).
					if (grayscale) 
					{
						Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2GRAY);
					}
					// Histogram equalization of the image (optional).
					if (histogramEqualization) 
					{
						if (grayscale) 
						{
							Imgproc.equalizeHist(image, image);
						} 
						else 
						{
							// Subdivision of the image in the three channels b, g and r.
							ArrayList<Mat> channels = new ArrayList<Mat>();
							Core.split(image, channels);

							Mat b = new Mat();
							Mat g = new Mat();
							Mat r = new Mat();

							// Histogram equalization for each individual channel.
							Imgproc.equalizeHist(channels.get(0), b);
							Imgproc.equalizeHist(channels.get(1), g);
							Imgproc.equalizeHist(channels.get(2), r);

							// Image reconstruction.
							ArrayList<Mat> normalizedImages = new ArrayList<Mat>();
							normalizedImages.add(b);
							normalizedImages.add(g);
							normalizedImages.add(r);
							Core.merge(normalizedImages, image);
						}
					}
					// Face detection and image cropping (optional).
					if (faceDetection) 
					{
						MatOfRect faces = new MatOfRect();
						// Verifies the presence of frontal faces through the haar front face classifier.
						frontalFaceCascade.detectMultiScale(image, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE, new Size(absoluteFaceSize, absoluteFaceSize), new Size());
	
						Rect[] facesArray = faces.toArray();
						// In the case that no frontal faces are detected and the user has chosen to also use the profile photo classifier, then any profile photos will be searched.
						if (facesArray.length == 0) 
						{
							if(profile == true)
							{
								// Verifies the presence of profile faces through the haar profile face classifier.
								profileFaceCascade.detectMultiScale(image, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE, new Size(absoluteFaceSize, absoluteFaceSize), new Size());
								facesArray = faces.toArray();
								// If no front or profile face is detected, the faceFound variable will be set to false.
								if (facesArray.length == 0) 
								{
									faceFound = false;
								}
							}
							else
							{
								faceFound = false;
							}
						}
						// If at least one face is detected, the photo will be cropped to the face itself.
						if (faceFound == true) 
						{
							Rect rectCrop = null;
							// Only the first face will be saved: if an image has more faces, the others are lost.
							if (facesArray[0].width > facesArray[0].height) 
							{
								rectCrop = new Rect(facesArray[0].x, facesArray[0].y, facesArray[0].width, facesArray[0].width);
							}
							else 
							{
								rectCrop = new Rect(facesArray[0].x, facesArray[0].y, facesArray[0].height,	facesArray[0].height);
							}
							// The face-only photo will be saved in a new matrix.
							Mat face = new Mat(image, rectCrop);
							// Scaling the photo to the desired size.
							Imgproc.resize(face, resizedFace, imageSize);
							// Release of the initialized variables.
							face.release();
						}
						// Release of the initialized variables.
						faces.release();
					} 
					else 
					{
						// Scaling the photo to the desired size.
						Imgproc.resize(image, resizedFace, imageSize);
					}
	
			    	// Photo classification phase.
					if (faceFound == true) 
					{
						if (file.getName().contains("angry")) 
						{
							Imgcodecs.imwrite(angerDirectory + "\\" + file.getName(), resizedFace);
						} 
						else if (file.getName().contains("contempt")) 
						{
							Imgcodecs.imwrite(contemptDirectory + "\\" + file.getName(), resizedFace);
						} 
						else if (file.getName().contains("disgusted")) 
						{
							Imgcodecs.imwrite(disgustDirectory + "\\" + file.getName(), resizedFace);
						} 
						else if (file.getName().contains("fearful")) 
						{
							Imgcodecs.imwrite(fearDirectory + "\\" + file.getName(), resizedFace);
						} 
						else if (file.getName().contains("happy")) 
						{
							Imgcodecs.imwrite(happinessDirectory + "\\" + file.getName(), resizedFace);
						} 
						else if (file.getName().contains("neutral")) 
						{
							Imgcodecs.imwrite(neutralityDirectory + "\\" + file.getName(), resizedFace);
						} 
						else if (file.getName().contains("sad")) 
						{
							Imgcodecs.imwrite(sadnessDirectory + "\\" + file.getName(), resizedFace);
						} 
						else if (file.getName().contains("surprised")) 
						{
							Imgcodecs.imwrite(surpriseDirectory + "\\" + file.getName(), resizedFace);
						}
						// Release of the initialized variables.
						resizedFace.release();
					}
					// Release of the initialized variables.
					image.release();
	
					// Increase the count of the number of photos classified (or, if not classified, of the analyzed photos).
					classified++;
					// Calculation of the percentage of completion of the current operation and update of the classification progress bar.
					percentage = (double) classified / (double) numberOfFiles;
					UpdateBar();
					// Next photo.
					i++;
				} 
				else 
				{
					ExceptionManager("The format of the images in the input file is not the one expected.");
			    	return;
				}
			}
			// If subdivision is active, the images will be divided between training, validation and test.
			if((subdivision)&&(!Thread.currentThread().isInterrupted()))
			{
				try 
				{
					Platform.runLater(() -> controller.setPhaseLabel("Phase 3: subdivision between training, validation and test folder..."));
					this.SubdivideImages(trainPercentage, validationPercentage, testPercentage);
				} 
				catch (SecurityException | IOException e) 
				{
					ExceptionManager("An error occurred during the subdivision.");
					return;
				}
			}
		}	
		// If a cancellation request has been made by the user, both temporary and classification folders will be deleted.
		if (Thread.currentThread().isInterrupted()) 
		{
			DeleteAllDirectories();	
			Platform.runLater(() -> controller.ShowAttentionDialog("Classification interrupted.\n"));
		}	
		// Otherwise only temporary ones will be deleted.
		else
		{
			if(subdivision)
			{
				Platform.runLater(() -> controller.setPhaseLabel("Phase 4: deleting temporary folders..."));
			}
			else
			{
				Platform.runLater(() -> controller.setPhaseLabel("Phase 3: deleting temporary folders..."));
			}
			DeleteTempDirectory();
		}
		
		Platform.runLater(() -> controller.StartStopClassification(false, false));
	}
}