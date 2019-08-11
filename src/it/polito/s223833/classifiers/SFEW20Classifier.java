package it.polito.s223833.classifiers;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javafx.application.Platform;

import org.apache.commons.io.comparator.NameFileComparator;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import it.polito.s223833.Controller;
import it.polito.s223833.utils.UnzipClass;

public class SFEW20Classifier extends Classifier implements Runnable 
{
	private String validationFile = "", testFile = "";
	private boolean defaultSubdivision = false, removeBadImages = false, squareImages = false;
	private int numberOfFiles = 0;
	private File classifiedImagesDirectory, trainDir, validationDir, testDir;
	public SFEW20Classifier(Controller controller, String trainFile, String validationFile, String testFile, String outputDirectory, int width, int height, int format, boolean grayscale, boolean histogramEqualization, int histogramEqualizationType, double tileSize, double contrastLimit, boolean removeBadImages, boolean squareImages, boolean defaultSubdivision, boolean subdivision, boolean validation, double trainPercentage, double validationPercentage, double testPercentage) 
	{
		super(controller, trainFile, outputDirectory, false, true, width, height, format, grayscale, histogramEqualization, histogramEqualizationType, tileSize, contrastLimit, false, subdivision, validation, trainPercentage, validationPercentage, testPercentage);
		this.validationFile = validationFile;
		this.testFile = testFile;
		this.removeBadImages = removeBadImages;
		this.squareImages = squareImages;
		this.defaultSubdivision = defaultSubdivision;
	}

	@Override
	public void run() 
	{
		// Extraction of the images of the SFEW 2.0 database.
		UnzipClass unzipper = new UnzipClass();
		try 
		{
			Platform.runLater(() -> controller.setPhaseLabel("Phase 1: extraction of the SFEW 2.0 train images..."));
			unzipper.Unzip(controller, inputFile, tempDirectory);
			Platform.runLater(() -> controller.setPhaseLabel("Phase 2: extraction of the SFEW 2.0 validation images..."));
			unzipper.Unzip(controller, validationFile, tempDirectory);
			Platform.runLater(() -> controller.setPhaseLabel("Phase 3: extraction of the SFEW 2.0 test images..."));
			unzipper.Unzip(controller, testFile, tempDirectory);
		} 
		catch (IOException e1) 
		{
			ExceptionManager("There was an error during the extraction of the MUG files.");
			return;
		}

		// Verify if the user has requested the cancellation of the current operation during the extraction phase.
		if (!Thread.currentThread().isInterrupted()) 
		{
			int i, j, k;
			String directoryName;
			// If defaultSubdivision is true two folders will be created: one for train and one for validation.
			if(defaultSubdivision)
			{
				// Create the train directory and the classification subfolders.
				trainDir = new File(outputDirectory+"\\Train");
				trainDir.mkdirs();
				CreateDirectoriesWithParameterAndWithoutInitialization(trainDir);
				// Create the validation directory and the classification subfolders.
				validationDir = new File(outputDirectory+"\\Validation");
				validationDir.mkdirs();
				CreateDirectoriesWithParameterAndWithoutInitialization(validationDir);
				
				directoryName = "Test";
			}
			// Otherwise a new folder will be created for the classified images.
			else
			{				
				classifiedImagesDirectory = new File(outputDirectory, "Classified Images");
				classifiedImagesDirectory.mkdirs();
				CreateDirectoriesWithParameter(classifiedImagesDirectory);
				
				directoryName = "OriginalTestFolder";
			}
			// Create the test directory.
			testDir = new File(outputDirectory+"\\"+directoryName);
			testDir.mkdirs();
			
			// Count of the number of files.
			File externalFolders = new File(tempDirectory);
			File[] listOfExternalFolders = externalFolders.listFiles();
			// Sort the external folder in ascending order, in order to have the test folder as first.
			Arrays.sort(listOfExternalFolders, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
			// Get the test images and update the total number of images.
			File[] testImages = listOfExternalFolders[0].listFiles();
			numberOfFiles = numberOfFiles + testImages.length;
			// Get the number of images in the train and validation folder.
			for (i = 1; i < listOfExternalFolders.length; i++)
			{
				File internalFolder = listOfExternalFolders[i];
				File[] listOfInternalFolders = internalFolder.listFiles();
				j = 0;
				while ((j < listOfInternalFolders.length)&&(!Thread.currentThread().isInterrupted()))
				{
					numberOfFiles = numberOfFiles + listOfInternalFolders[j].list().length;	
					j++;
				}
			}
			
			if(!Thread.currentThread().isInterrupted())
			{
				Platform.runLater(() -> controller.setPhaseLabel("Phase 4: execution of the operations chosen by the user..."));
			}
			
			// Do the operations chosen by the user on the images in the test folder.
			i = 0;
			while ((i < testImages.length) && (!Thread.currentThread().isInterrupted()))
			{
				DoOperationsOnImage(testImages[i], 0);
				i++;
			}
				
			// Do the operations chosen by the user on the images in the train and validation folder.
			// Cycle for train and validation folders.
			for (i = 1; i < listOfExternalFolders.length; i++)
			{
				File[] listOfInternalFolders = listOfExternalFolders[i].listFiles();	
				j = 0;
				
				// Cycle for classification folders.
				while ((j < listOfInternalFolders.length) && (!Thread.currentThread().isInterrupted()))
				{
					File[] listOfImages = listOfInternalFolders[j].listFiles();						
					k = 0;
					
					// Cycle for images.
					while ((k < listOfImages.length) && (!Thread.currentThread().isInterrupted()))
					{
						DoOperationsOnImage(listOfImages[k], i);
						k++;
					}
					
					j++;
				}		
			}
		}
		// If subdivision is active, the images will be divided between train, validation and test.
		if ((subdivision) && (!Thread.currentThread().isInterrupted())) 
		{
			Platform.runLater(() -> controller.setPhaseLabel("Phase 2: subdivision between train, validation and test folder..."));
			if (!SubdivideImages(trainPercentage, validationPercentage, testPercentage)) 
			{
				ExceptionManager("There was an error while performing the subdivision.");
				return;
			}
			try
			{
				classifiedImagesDirectory.delete();
			}
			catch(SecurityException e)
			{
				Platform.runLater(() -> {controller.ShowErrorDialog("An error occurred while deleting temporary directories.");});
			}
		}
		boolean error = false;
		// If a cancellation request has been made by the user, both temporary and classification folders will be deleted.
		if (Thread.currentThread().isInterrupted()) 
		{
			error = DeleteAllDirectories();
			Platform.runLater(() -> controller.ShowAttentionDialog("Classification interrupted.\n"));
		}
		// Otherwise only the temporary one will be deleted.
		else 
		{
			if (subdivision) 
			{
				Platform.runLater(() -> controller.setPhaseLabel("Phase 6: deleting temporary folders..."));
			} 
			else 
			{
				Platform.runLater(() -> controller.setPhaseLabel("Phase 5: deleting temporary folders..."));
			}
			error = DeleteTempDirectory();
		}
		// Reset the buttons if no errors occured.
		if (!error)
		{
			Platform.runLater(() -> controller.StartStopClassification(false, false));
		}
	}
	
	/* Method for performing the operation on the input file.*/
	void DoOperationsOnImage(File inputImage, int folder)
	{
		boolean discardImage = false;
		Mat image = Imgcodecs.imread(inputImage.getAbsolutePath());
		
		// Make image square (optional).
		if (squareImages) 
		{
			image = MakeImageSquare(image);
		}
		
		// Resize the image.
		Imgproc.resize(image, image, imageSize);
		
		// Conversion of the image in grayscale (optional).
		if (grayscale) 
		{
			Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2GRAY);
		}

		// Histogram equalization of the image (optional).
		if (histogramEqualization) 
		{
			image = HistogramEqualization(image);
		}	
		
		// If removeBadImages is true, check if the current image is one of those that should be ignored.
		if(removeBadImages)
		{
			discardImage = CompareImageName(inputImage.getAbsolutePath().substring(inputImage.getAbsolutePath().lastIndexOf("\\")+1));
		}
		if(!discardImage)
		{
			// Enter if the image is from the test folder.
			if(folder == 0)
			{
				SaveImageInTheChosenFormat(testDir.getAbsolutePath(), inputImage.getName(), image);
			}
			// Else the image is from the train or validation folder.
			else
			{
				File outputDir = null;
				if(!defaultSubdivision)
				{
					outputDir = classifiedImagesDirectory;
				}
				else
				{
					// Enter if the image is from the train folder.
					if (folder == 1)
					{
						outputDir = trainDir;
					}
					// Else the image is from the validation folder.
					else
					{
						outputDir = validationDir;
					}
				}
				
				// Save the image in the right classification folder.
				String dirName=inputImage.getParentFile().getAbsolutePath().substring(inputImage.getParentFile().getAbsolutePath().lastIndexOf("\\")+1);
				if (dirName.equals("Angry"))
				{
					SaveImageInTheChosenFormat(outputDir+"\\Anger", inputImage.getName(), image);
				}
				else if (dirName.equals("Disgust"))
				{
					SaveImageInTheChosenFormat(outputDir+"\\Disgust", inputImage.getName(), image);
				}
				else if (dirName.equals("Fear"))
				{
					SaveImageInTheChosenFormat(outputDir+"\\Fear", inputImage.getName(), image);
				}
				else if (dirName.equals("Happy"))
				{
					SaveImageInTheChosenFormat(outputDir+"\\Happiness", inputImage.getName(), image);
				}
				else if (dirName.equals("Neutral"))
				{
					SaveImageInTheChosenFormat(outputDir+"\\Neutrality", inputImage.getName(), image);
				}
				else if (dirName.equals("Sad"))
				{
					SaveImageInTheChosenFormat(outputDir+"\\Sadness", inputImage.getName(), image);
				}
				else if (dirName.equals("Surprise"))
				{
					SaveImageInTheChosenFormat(outputDir+"\\Surprise", inputImage.getName(), image);
				}
			}		
		}		
		
		// Increase the count of the number of classified photos.
		classified++;
		// Calculation of the percentage of completion of the current operation and update of the classification progress bar.
		percentage = (double) classified / (double) numberOfFiles;
		UpdateBar();
	}
	
	//Method for compare the name of the images with the ones to be deleted (130).
	boolean CompareImageName(String name)
	{
		// Anger (19).
		if(name.equals("21_012239520_00000041.png") || name.equals("Aviator_011504520_00000027.png") || 
				name.equals("Bridesmaids_000633160_00000001.png") || name.equals("Contraband_011817120_00000011.png") ||
				name.equals("GirlWithAPearlEarring_012432920_00000021.png") || name.equals("Hangover_004512014_00000002.png") ||
				name.equals("NanyDiaries_012335760_00000006.png") || name.equals("OceansEleven_001637640_00000014.png") ||
				name.equals("OceansThirteen_000550480_00000018.png") || name.equals("OceansThirteen_000550480_00000058.png") ||
				name.equals("RevolutionaryRoad_000654280_00000019.png") || name.equals("RevolutionaryRoad_000654280_00000045.png") ||
				name.equals("RevolutionaryRoad_000806440_00000015.png") || name.equals("RevolutionaryRoad_000806440_00000030.png") ||
				name.equals("RevolutionaryRoad_000806440_00000038.png") || name.equals("RevolutionaryRoad_000806440_00000052.png") ||
				name.equals("SomethingBorrowed_011859880_00000042.png") || name.equals("Town_010805120_00000031.png") ||
				name.equals("Unstoppable_004417618_00000027.png") ||
				// Disgust (5).
				name.equals("MissMarch_000157760_00000017.png") || name.equals("OceansThirteen_000923600_00000024.png") || 
				name.equals("OceansThirteen_000923600_00000054.png") || name.equals("OceansThirteen_010619400_00000057.png") ||
				name.equals("ThereIsSomethingAboutMary_014003120_00000019.png") ||
				// Fear (6).
				name.equals("CryingGame_001143440_00000048.png") || name.equals("DeepBlueSea_004442200_00000001.png") ||
				name.equals("Hangover_001949614_00000089.png") || name.equals("HarryPotter_PhilosopherStone_000738534_00000001.png") ||
				name.equals("JennifersBody_001947263_00000009.png") || name.equals("OceansThirteen_014012360_00000031.png") ||
				// Happiness (24).
				name.equals("21_001010160_00000010.png") || name.equals("21_001010160_00000011.png") ||
				name.equals("21_001010160_00000017.png") ||	name.equals("21_001010160_00000021.png") ||
				name.equals("Bridesmaids_000059880_00000018.png") || name.equals("Bridesmaids_015117000_00000001.png") ||
				name.equals("ChangeUp_000451280_00000014.png") || name.equals("Hangover_004104894_00000041.png") ||
				name.equals("Hangover_005242454_00000016.png") || name.equals("Hangover_013036534_00000011.png") ||
				name.equals("Hangover_013036534_00000022.png") || name.equals("HarryPotter_Half_Blood_Prince_001259094_00000033.png") ||
				name.equals("HarryPotter_Half_Blood_Prince_005745534_00000016.png") || name.equals("ItsComplicated_000836607_00000001.png") ||
				name.equals("ItsComplicated_000857887_00000049.png") || name.equals("ItsComplicated_001242687_00000009.png") ||
				name.equals("MissMarch_002459400_00000095.png") || name.equals("NotSuitableForChildren_004004880_00000021.png") ||
				name.equals("NotSuitableForChildren_004004880_00000036.png") || name.equals("RememberMe_001001280_00000038.png") ||
				name.equals("Serendipity_001925727_00000001.png") || name.equals("Serendipity_001925727_00000059.png") ||
				name.equals("ThereIsSomethingAboutMary_002830120_00000001.png") || name.equals("YouveGotAMail_004212614_00000001.png") ||
				//Neutrality (8).
				name.equals("DecemberBoys_002006160_00000052.png") || name.equals("FriendsWithBenefit_010944320_00000001.png") || 
				name.equals("NanyDiaries_013247840_00000002.png") || name.equals("OceansEleven_000509960_00000072.png") ||
				name.equals("OceansThirteen_014812080_00000007.png") || name.equals("OceansTwelve_001942600_00000013.png") ||
				name.equals("Terminal_014601640_00000037.png") || name.equals("Unstoppable_010135638_00000032.png") ||
				// Sadness (34).
				name.equals("21_012246400_00000087.png") || name.equals("AlexEmma_012701000_00000070.png") ||
				name.equals("CryingGame_001419640_00000062.png") || name.equals("CryingGame_001419640_00000070.png") ||
				name.equals("DecemberBoys_003708960_00000027.png") || name.equals("Descendants_002643280_00000048.png") ||
				name.equals("Descendants_003221880_00000040.png") || name.equals("FriendsWithBenefit_013139640_00000016.png") ||
				name.equals("FriendsWithBenefit_013714720_00000005.png") || name.equals("FriendsWithBenefit_013714720_00000023.png") ||
				name.equals("Hangover_011616534_00000027.png") || name.equals("HarryPotter_Deathly_Hallows_1_005528520_00000001.png") ||
				name.equals("HarryPotter_Deathly_Hallows_1_005528520_00000026.png") || name.equals("IAmSam_012921760_00000084.png") ||
				name.equals("IAmSam_015653680_00000052.png") || name.equals("JennifersBody_012437771_00000033.png") ||
				name.equals("LittleManhattan_000150854_00000041.png") || name.equals("OneFlewOverCuckooNest_001733000_00000001.png") ||
				name.equals("OneFlewOverCuckooNest_001733000_00000064.png") || name.equals("OneFlewOverCuckooNest_013610680_00000016.png") ||
				name.equals("PrettyInPink_000543360_00000023.png") || name.equals("PrettyInPink_000543360_00000033.png") ||
				name.equals("PrettyInPink_011006040_00000004.png") || name.equals("PrettyInPink_011006040_00000010.png") ||
				name.equals("PrettyInPink_011006040_00000027.png") || name.equals("PrettyInPink_011006040_00000049.png") ||
				name.equals("PrettyInPink_011823760_00000058.png") || name.equals("RememberMe_011901800_00000002.png") ||
				name.equals("RememberMe_011901800_00000003.png") || name.equals("Town_001305880_00000001.png") ||
				name.equals("Town_001305880_00000017.png") || name.equals("Terminal_014001000_00000063.png") ||
				name.equals("Terminal_014005640_00000001.png") || name.equals("v_001851280_00000031.png") ||
				// Surprise (14).
				name.equals("21_001404920_00000004.png") || name.equals("21_001404920_00000016.png") || 
				name.equals("21_002221960_00000007.png") || name.equals("AboutABoy_000919327_00000007.png") ||
				name.equals("AboutABoy_000919327_00000013.png") || name.equals("AboutABoy_000919327_00000015.png") ||
				name.equals("Bridesmaids_000059880_00000001.png") || name.equals("GirlWithAPearlEarring_003258400_00000031.png") ||
				name.equals("MissMarch_000157760_00000024.png") || name.equals("MissMarch_002519000_00000015.png") ||
				name.equals("NanyDiaries_000814440_00000074.png") || name.equals("Unstoppable_004406878_00000003.png") ||
				name.equals("Unstoppable_004406878_00000006.png") || name.equals("Unstoppable_004406878_00000020.png") ||
			
				//Test Folder (20).
				name.equals("ChildrenOfMen_002648320_00000003.png") || name.equals("EvilDead_002039000_00000038.png") ||
				name.equals("Grudge2_005102342_00000011.png") || name.equals("Grudge2_005102342_00000015.png") ||
				name.equals("Grudge2_005102342_00000018.png") || name.equals("Grudge3_001148800_00000027.png") ||
				name.equals("HillHaveEyes2_002811360_00000013.png") || name.equals("HillHaveEyes2_010941280_00000037.png") ||
				name.equals("HouseOfWax_001413640_00000015.png") || name.equals("Orphan_011442920_00000003.png") ||
				name.equals("Orphan_011442920_00000037.png") || name.equals("Orphan_011442920_00000046.png") ||
				name.equals("Orphan_014220640_00000023.png") || name.equals("Quartet_005755240_00000012.png") ||
				name.equals("Quartet_012045360_00000019.png") || name.equals("TakingLives_005355240_00000001.png") ||
				name.equals("TheCaller_000614400_00000001.png") || name.equals("TheHaunting_002319000_00000031.png") ||
				name.equals("TheHaunting_010836440_00000001.png") || name.equals("VanillaSky_003224840_00000039.png"))
		{
			return true;
		}
		return false;
	}
}