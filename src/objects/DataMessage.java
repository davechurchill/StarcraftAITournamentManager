package objects;

import java.io.File;
import java.io.IOException;

import utility.*;

public class DataMessage implements Message
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -8193083217816970522L;
	public DataType type;		// the type of data
	public byte[] data;			// the data
	public String botName;		// associated bot name (if necessary)
	public String zipFileName = "";
	
	public DataMessage(DataType type, String src) throws IOException
	{
		this.type = type;
		
		// Required and Map Files are in zip files already, so load from a zip
		if (type == DataType.REQUIRED_DIR || type == DataType.MAPS)
		{
			zipFileName = src;
			readZipFile(src);
		}
		else
		{
			// otherwise we need to zip the directory
			read(src);
		}
		
	}
	
	public DataMessage(DataType type, byte [] data)
	{
		this.type = type;
		
		this.data = data;
		
	}
	
	public DataMessage(DataType type, String botName, String src) throws IOException
	{
		this.type = type;
		this.botName = botName;
		read(src);
	}
	
	public byte[] getRawData()
	{
		return data;
	}
	
	public String toString()
	{
		String extra = " " + (botName == null ? new File(zipFileName).getName() : botName);		
		
		return "" + type + extra + " " + (data.length/1000) + " kb";
	}
	
	public void read(String src) throws IOException
	{
		data = ZipTools.ZipDirToByteArray(src);
	}
	
	public void readZipFile(String src) throws IOException
	{
		data = ZipTools.LoadZipFileToByteArray(src);
	}
	
	public void write(String dest) throws IOException
	{
		ZipTools.UnzipByteArrayToDir(data, dest);
	}
}