package com.mycompany.cascade.plugin;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.cms.assetfactory.BaseAssetFactoryPlugin;
import com.cms.assetfactory.FatalPluginException;
import com.cms.assetfactory.PluginException;
import com.hannonhill.cascade.api.asset.admin.AssetFactory;
import com.hannonhill.cascade.api.asset.admin.User;
import com.hannonhill.cascade.api.asset.common.Identifier;
import com.hannonhill.cascade.api.asset.home.File;
import com.hannonhill.cascade.api.asset.home.Folder;
import com.hannonhill.cascade.api.asset.home.FolderContainedAsset;
import com.hannonhill.cascade.api.operation.Create;
import com.hannonhill.cascade.api.operation.Read;
import com.hannonhill.cascade.api.operation.result.ReadOperationResult;
import com.hannonhill.cascade.model.dom.identifier.EntityType;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.commons.util.StringUtil;

/**
 * This is a webp Plugin
 */
public final class WebpPlugin extends BaseAssetFactoryPlugin
{
    /** The resource bundle key for the name of the plugin */
    private static final String NAME_KEY = "assetfactory.plugin.webp.name";
    /** The resource bundle key for the description of the plugin */
    private static final String DESC_KEY = "assetfactory.plugin.plugin.webp.description";
    /** The resource bundle key for the name of a parameter */
    private static final String PARAM_IMAGEQUALITY_NAME_KEY = "assetfactory.plugin.webp.parameter.imagequality.name";
    /** The resource bundle key for the description of a parameter */
    private static final String PARAM_IMAGEQUALITY_DESCRIPTION_KEY = "assetfactory.plugin.webp.parameter.imagequality.description";

    
    /** The number of additional images that will be created */
    private static final String PARAM_NUM_ADDITIONAL_IMAGES_NAME_KEY = "assetfactory.plugin.webp.param.name.numadditionalimages";
    private static final String PARAM_NUM_ADDITIONAL_IMAGES_DESCRIPTION_KEY = "assetfactory.plugin.webp.param.description.numadditionalimages";

    /** Comma delimited list of widths of the new images that will be created */
    private static final String PARAM_WIDTHS_NAME_KEY = "assetfactory.plugin.webp.param.name.width";
    private static final String PARAM_WIDTHS_DESCRIPTION_KEY = "assetfactory.plugin.webp.param.description.width";

    /** Comma delimited list of heights of the new images that will be created */
    private static final String PARAM_HEIGHTS_NAME_KEY = "assetfactory.plugin.webp.param.name.height";
    private static final String PARAM_HEIGHTS_DESCRIPTION_KEY = "assetfactory.plugin.webp.param.description.height";

   
    /* (non-Javadoc)
     * @see com.cms.assetfactory.BaseAssetFactoryPlugin#doPluginActionPost(com.hannonhill.cascade.api.asset.admin.AssetFactory, com.hannonhill.cascade.api.asset.home.FolderContainedAsset)
     */
    @Override
    public void doPluginActionPost(AssetFactory factory, FolderContainedAsset asset) throws PluginException
    {
        User user = getCurrentUser();
        Folder parent = asset.getParentFolder();
        
        if (factory.getWorkflowMode() == AssetFactory.WORKFLOW_MODE_FOLDER_CONTROLLED && !parent.isNoWorkflowRequired()
                && !user.canBypassWorkflow(asset.getSiteId()))
            throw new FatalPluginException("You cannot create this asset in this Folder because this Folder requires a Workflow");
    	
		EntityType type = asset.getIdentifer().getType();
		
		if (type != EntityTypes.TYPE_FILE)
				throw new PluginException("Not a File");
	
		File file = (File) asset;
//        FileExtension ext = new FileExtension(file.getName());

        final byte[] originalFileData = file.getData();
        final String originalFileName = file.getName();
        final String originalFileNameNoExt= file.getName().substring(0, file.getName().length()-4);
        
        
		java.io.File tempFile;
		java.io.File tempWebPFile;
        BufferedImage originalFileAsBufferedImage = null;
		try {
			// Get Original image into buffer			
			originalFileAsBufferedImage = ImageIO.read(new ByteArrayInputStream(file.getData()));
		} catch (IOException e) {
			throw new PluginException("Unable to read file contents: " + e.getMessage(), e);
		}
		
		try
		{

			if (originalFileAsBufferedImage == null)
                throw new PluginException("File is not a supported image type.  Supported image types are JPG or PNG.");

			// Store Original Dimensions to calculate new size. 
            final Dimension originalDimensions = new Dimension(originalFileAsBufferedImage.getWidth(), originalFileAsBufferedImage.getHeight());
            final int numAdditionalImages = getParameter(PARAM_NUM_ADDITIONAL_IMAGES_NAME_KEY) == null ? 0 : Integer.parseInt(getParameter(PARAM_NUM_ADDITIONAL_IMAGES_NAME_KEY));


            String widthsStr = getParameter(PARAM_WIDTHS_NAME_KEY);
            String heightStr = getParameter(PARAM_HEIGHTS_NAME_KEY);
            String quality = getParameter(PARAM_IMAGEQUALITY_NAME_KEY);

            if (widthsStr == null)
                widthsStr = "";
            if (heightStr == null)
                heightStr = "";
            if (quality == null)
            	quality = "75";

            //Split different image sizes by commas if found
            final String[] widths = widthsStr.split(",");
            final String[] heights = heightStr.split(",");

            //Create Temp Source Image
            tempFile = java.io.File.createTempFile("fileUpload-", file.getName().substring(0, file.getName().length()-4) +"-"+ getUsername() +"#"+ user.getIdentifer());
			
            // Copy over files data to to temp source image 
			try( FileOutputStream stream = new FileOutputStream(tempFile) )
			{
				stream.write(file.getData());
				stream.flush();
				stream.close();
			}
			
			if(!tempFile.exists())
				throw new PluginException("Could not find source file");
			
			// Create First Target Webp Temp Image
			tempWebPFile = java.io.File.createTempFile("webp-", originalFileNameNoExt +"-"+ getUsername() +"#"+ user.getIdentifer().getId() +".webp");

			// Run cwebp passing over quality and source uri and target uri
	    	ProcessBuilder pb = new ProcessBuilder("cwebp", "-q", quality, "\""+ tempFile.getAbsolutePath() +"\"", "-o", "\""+ tempWebPFile.getAbsolutePath() +"\"");
			Process p = pb.start();
			
			//wait for process to finish/or 4 secs			
			p.waitFor(4, TimeUnit.SECONDS);
			
			// We will reuse the cascade file passing in the new bytes and creating a new webp image. 
			file.setData(Files.readAllBytes(Paths.get(tempWebPFile.getAbsolutePath())));
			file.setName(originalFileNameNoExt +".webp");
			
			//Create file
			persistNewImage(file, getUsername());
			
			//Delete old temp file after creation in cascade. 
	    	tempWebPFile.delete();
	    	
	    	// Now loop thru addition images. 
            for (int i = 0; i < numAdditionalImages; i++)
            {
            	// Get the new image size for this image
                String height = i < heights.length ? heights[i].trim() : "";
                String width = i < widths.length ? widths[i].trim() : "";
                Dimension newDimensions = getNewImageDimensions(originalDimensions, height, width);
                
                //Create temp webp file
    			tempWebPFile = java.io.File.createTempFile("webp-", originalFileNameNoExt +"-"+ getUsername() +"#"+ asset.getIdentifer().getId()  +".webp");

    			// Run cwebp to build the webp image and wait 4 secs/or command to finish
    	    	pb = new ProcessBuilder("cwebp", "-q", quality, "-resize", ""+newDimensions.width, ""+newDimensions.height,  "\""+ tempFile.getAbsolutePath() +"\"", "-o", "\""+ tempWebPFile.getAbsolutePath() +"\"");
    			p = pb.start();
    			p.waitFor(4, TimeUnit.SECONDS);
    			
    			// reuse file to create new cascade file
    			file.setData(Files.readAllBytes(Paths.get(tempWebPFile.getAbsolutePath())));
    			file.setName(createNewName(originalFileNameNoExt, "webp", newDimensions));
    			
    			persistNewImage(file, getUsername());
    			
    	    	tempWebPFile.delete();
            }
            
            // Delete the orignal temp source file, and flush image buffer
	    	tempFile.deleteOnExit();
	    	tempFile.delete();	    	

            originalFileAsBufferedImage.flush();
		} catch (IOException e) {
			throw new PluginException(e);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			throw new PluginException(e);
		}
		
		//Reset cascade file back to its original so it can complete its upload. 
        file.setName(originalFileName);
        file.setData(originalFileData);
    }

    /**
     * Generate a new filename with dimensions built into the name. 
     * @param filename
     * @param extension
     * @param newDimensions
     * @return
     */
    private static final String createNewName(final String filename, final String extension, final Dimension newDimensions)
    {
        return StringUtil.concat(filename, "-", newDimensions.width, "x", newDimensions.height, ".", extension);
    }
    
    
    /* (non-Javadoc)
     * @see com.cms.assetfactory.BaseAssetFactoryPlugin#doPluginActionPre(com.hannonhill.cascade.api.asset.admin.AssetFactory, com.hannonhill.cascade.api.asset.home.FolderContainedAsset)
     */
    @Override
    public void doPluginActionPre(AssetFactory factory, FolderContainedAsset asset) throws PluginException
    {
        //code in this method will be executed before the user is presented with the
        //initial edit screen. This could be used for pre-population, etc.
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getAvailableParameterDescriptions()
     */
    public Map<String, String> getAvailableParameterDescriptions()
    {
        //build a map where the keys are the names of the parameters
        //and the values are the descriptions of the parameters
        Map<String, String> paramDescriptionMap = new HashMap<String, String>();
        paramDescriptionMap.put(PARAM_IMAGEQUALITY_NAME_KEY, PARAM_IMAGEQUALITY_DESCRIPTION_KEY);
        paramDescriptionMap.put(PARAM_NUM_ADDITIONAL_IMAGES_NAME_KEY, PARAM_NUM_ADDITIONAL_IMAGES_DESCRIPTION_KEY);
        paramDescriptionMap.put(PARAM_WIDTHS_NAME_KEY, PARAM_WIDTHS_DESCRIPTION_KEY);
        paramDescriptionMap.put(PARAM_HEIGHTS_NAME_KEY, PARAM_HEIGHTS_DESCRIPTION_KEY);
        return paramDescriptionMap;
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getAvailableParameterNames()
     */
    public String[] getAvailableParameterNames()
    {
        //return a string array with all the name keys of
        //the parameters for the plugin
        return new String[] { PARAM_IMAGEQUALITY_NAME_KEY, PARAM_NUM_ADDITIONAL_IMAGES_NAME_KEY, PARAM_WIDTHS_NAME_KEY, PARAM_HEIGHTS_NAME_KEY };
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getDescription()
     */
    public String getDescription()
    {
        return DESC_KEY;
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getName()
     */
    public String getName()
    {
        return NAME_KEY;
    }
    
    
   
    /**
     * Gets the new image dimensions by parsing the newHeightStr and newWidthStr.
     * 
     * @param originalDimensions the original dimensions, needed if newHeightStr or newWidthStr are a
     *        percentage.
     * @param newHeightStr a string describing the new height (a percentage if it contains a '%', a pixel
     *        value otherwise)
     * @param newWidthStr a string describing the new width (a percentage if it contains a '%', a pixel value
     *        otherwise)
     * @return
     */
    private final Dimension getNewImageDimensions(final Dimension originalDimensions, String newHeightStr, String newWidthStr) throws PluginException
    {
        int newWidth = -1;
        int newHeight = -1;

        /* simplify logic */
        if (newWidthStr == null)
            newWidthStr = "";
        if (newHeightStr == null)
            newHeightStr = "";

        /* check if both parameters are empty */
        if (newWidthStr.equals("") && newHeightStr.equals(""))
        {
            newWidth = originalDimensions.width;
            newHeight = originalDimensions.height;

        }

        /* parse the width parameter */
        try
        {
            if (newWidthStr.length() > 0 && newWidthStr.charAt(newWidthStr.length() - 1) == '%')
            {
                newWidthStr = newWidthStr.substring(0, newWidthStr.length() - 1);
                newWidth = originalDimensions.width * Integer.parseInt(newWidthStr) / 100;
            }
            else if (newWidthStr.length() > 0)
            {
                newWidth = Integer.parseInt(newWidthStr);
            }
        }
        catch (NumberFormatException nfe)
        {
            throw new PluginException("Unable to parse width parameter: " + newWidthStr, nfe);
        }

        /* parse the height parameter */
        try
        {
            if (newHeightStr.length() > 0 && newHeightStr.charAt(newHeightStr.length() - 1) == '%')
            {
                newHeightStr = newHeightStr.substring(0, newHeightStr.length() - 1);
                newHeight = originalDimensions.height * Integer.parseInt(newHeightStr) / 100;

            }
            else if (newHeightStr.length() > 0)
            {
                newHeight = Integer.parseInt(newHeightStr);
            }
        }
        catch (NumberFormatException nfe)
        {
            throw new PluginException("Unable to parse height parameter: " + newHeightStr, nfe);
        }

        try
        {
            if (newWidth == -1)
                newWidth = originalDimensions.width * newHeight / originalDimensions.height;
            if (newHeight == -1)
                newHeight = originalDimensions.height * newWidth / originalDimensions.width;

        }
        catch (ArithmeticException ae)
        {
            throw new PluginException("Original dimensions cannot be zero");
        }

        return new Dimension(newWidth, newHeight);
    }
    
    
    /**
     * Persists the file described by the FileModelBean
     * 
     * @param newFile the FileModelBean containing the information to persist.
     * @param username the username of the user creating the file
     * @throws FatalPluginException
     */
    private static final void persistNewImage(File newFile, String username) throws FatalPluginException
    {
        Create create = new Create();
        create.setUsername(username);
        create.setAsset(newFile);
        create.setInstantiateWorkflow(false);
        create.setCreateNewInstance(true);
        try
        {
            create.perform();
        }
        catch (Exception e)
        {
            throw new FatalPluginException("Unable to create a resized copy: " + e.getMessage(), e);
        }
    }
    
    /**
     * Returns current user
     * 
     * @return
     * @throws PluginException
     */
    private User getCurrentUser() throws PluginException
    {
        Read read = new Read();
        Identifier identifier = new Identifier()
        {
            public String getId()
            {
                return getUsername();
            }

            public EntityType getType()
            {
                return EntityTypes.TYPE_USER;
            }
        };

        read.setToRead(identifier);
        read.setUsername(getUsername());
        try
        {
            ReadOperationResult result = (ReadOperationResult) read.perform();
            return (User) result.getAsset();
        }
        catch (Exception e)
        {
            throw new PluginException("Unable to read the user: " + e.getMessage());
        }
    }

}
